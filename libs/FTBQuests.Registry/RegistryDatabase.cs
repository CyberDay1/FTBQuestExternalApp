using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using FTBQuests.Codecs.Model;
using FTBQuests.Registry.Model;
using FTBQuests.Core.Model;

namespace FTBQuests.Registry;

/// <summary>
/// Provides in-memory lookups over registry entries imported from the probe output.
/// </summary>
public sealed class RegistryDatabase
{
    private static readonly RegistryItem[] EmptyItems = Array.Empty<RegistryItem>();

    private readonly Dictionary<string, RegistryItem> itemsById;
    private static readonly IComparer<RegistryItem> IdentifierComparer =
        Comparer<RegistryItem>.Create((left, right) => string.CompareOrdinal(left.ToString(), right.ToString()));

    private readonly Dictionary<string, RegistryItem[]> itemsByTag;
    private readonly IReadOnlyDictionary<string, IReadOnlyCollection<string>> tagMembership;
    private readonly Dictionary<string, List<RegistryItem>> itemsBySourceMod;
    private readonly List<RegistryItem> orderedItems;

    public RegistryDatabase(IEnumerable<RegistryItem> items, IReadOnlyDictionary<string, IReadOnlyCollection<string>> tagMembership)
    {
        ArgumentNullException.ThrowIfNull(items);
        ArgumentNullException.ThrowIfNull(tagMembership);

        itemsById = new Dictionary<string, RegistryItem>(StringComparer.OrdinalIgnoreCase);
        var bySource = new Dictionary<string, List<RegistryItem>>(StringComparer.OrdinalIgnoreCase);

        foreach (RegistryItem item in items)
        {
            itemsById[item.ToString()] = item;

            if (!bySource.TryGetValue(item.SourceModId, out List<RegistryItem>? sourceItems))
            {
                sourceItems = new List<RegistryItem>();
                bySource[item.SourceModId] = sourceItems;
            }

            sourceItems.Add(item);
        }

        itemsBySourceMod = new Dictionary<string, List<RegistryItem>>(StringComparer.OrdinalIgnoreCase);
        foreach (var kvp in bySource)
        {
            var sourceMod = kvp.Key;
            var sourceItems = kvp.Value;
            sourceItems.Sort(IdentifierComparer);
            itemsBySourceMod[sourceMod] = sourceItems;
        }

        var normalizedMembership = new Dictionary<string, IReadOnlyCollection<string>>(StringComparer.OrdinalIgnoreCase);
        foreach (var kvp in tagMembership)
        {
            string tag = kvp.Key;
            IReadOnlyCollection<string> identifiers = kvp.Value;

            if (string.IsNullOrWhiteSpace(tag))
                continue;

            string normalizedTag = tag.Trim();
            string[] normalizedIdentifiers = identifiers
                .Where(identifier => !string.IsNullOrWhiteSpace(identifier))
                .Select(identifier => identifier.Trim())
                .ToArray();

            normalizedMembership[normalizedTag] =
                normalizedIdentifiers.Length == 0 ? Array.Empty<string>() : normalizedIdentifiers;
        }

        this.tagMembership = new ReadOnlyDictionary<string, IReadOnlyCollection<string>>(normalizedMembership);

        itemsByTag = new Dictionary<string, RegistryItem[]>(StringComparer.OrdinalIgnoreCase);
        foreach (var kvp in normalizedMembership)
        {
            string tag = kvp.Key;
            IReadOnlyCollection<string> identifiers = kvp.Value;

            RegistryItem[] resolved = identifiers
                .Select(identifier => TryGetByIdentifier(identifier, out RegistryItem? match) ? match : null)
                .Where(item => item is not null)
                .Select(item => item!)
                .Distinct()
                .OrderBy(item => item.ToString(), StringComparer.Ordinal)
                .ToArray();

            itemsByTag[tag] = resolved;
        }

        orderedItems = itemsById.Values
            .OrderBy(item => item.ToString(), StringComparer.Ordinal)
            .ToList();
    }

    public IReadOnlyCollection<RegistryItem> Items => orderedItems;

    public bool TryGetByIdentifier(string identifier, out RegistryItem? item)
    {
        ArgumentException.ThrowIfNullOrEmpty(identifier);
        return itemsById.TryGetValue(identifier, out item);
    }

    public IReadOnlyList<RegistryItem> GetByTag(string tag)
    {
        ArgumentException.ThrowIfNullOrEmpty(tag);
        return itemsByTag.TryGetValue(tag, out RegistryItem[]? items) ? items : EmptyItems;
    }

    public IReadOnlyList<RegistryItem> GetBySourceModId(string sourceModId)
    {
        ArgumentException.ThrowIfNullOrEmpty(sourceModId);
        return itemsBySourceMod.TryGetValue(sourceModId, out List<RegistryItem>? sourceItems)
            ? sourceItems
            : EmptyItems;
    }

    public IReadOnlyList<RegistryItem> GetItemsByMod(string modId) => GetBySourceModId(modId);

    public IReadOnlyCollection<string> GetModIdentifiers() =>
        itemsBySourceMod.Keys.OrderBy(id => id, StringComparer.OrdinalIgnoreCase).ToList();

    public bool RemoveItem(Identifier id)
    {
        if (string.IsNullOrWhiteSpace(id.ToString()))
            throw new ArgumentException("Identifier cannot be empty.", nameof(id));

        if (!itemsById.Remove(id.ToString(), out RegistryItem? item))
            return false;

        RemoveItemInternal(item);
        return true;
    }

    public int RemoveItemsByMod(string modId)
    {
        ArgumentException.ThrowIfNullOrEmpty(modId);

        if (!itemsBySourceMod.TryGetValue(modId, out List<RegistryItem>? modItems) || modItems.Count == 0)
            return 0;

        var snapshot = modItems.ToArray();
        int removed = 0;
        foreach (RegistryItem item in snapshot)
        {
            if (!itemsById.Remove(item.ToString()))
                continue;

            RemoveItemInternal(item);
            removed++;
        }

        return removed;
    }

    public IReadOnlyDictionary<string, IReadOnlyCollection<string>> TagMembership => this.tagMembership;

    public void AddIfMissing(RegistryItem item)
    {
        ArgumentNullException.ThrowIfNull(item);

        if (itemsById.ContainsKey(item.ToString()))
            return;

        itemsById[item.ToString()] = item;
        InsertOrdered(orderedItems, item);

        if (!itemsBySourceMod.TryGetValue(item.SourceModId, out List<RegistryItem>? sourceItems))
        {
            sourceItems = new List<RegistryItem>();
            itemsBySourceMod[item.SourceModId] = sourceItems;
        }

        InsertOrdered(sourceItems, item);
    }

    private static void InsertOrdered(List<RegistryItem> list, RegistryItem item)
    {
        int index = list.BinarySearch(item, IdentifierComparer);
        if (index < 0)
            index = ~index;
        list.Insert(index, item);
    }

    private static void RemoveOrdered(List<RegistryItem> list, RegistryItem item)
    {
        int index = list.BinarySearch(item, IdentifierComparer);
        if (index >= 0)
            list.RemoveAt(index);
    }

    private void RemoveItemInternal(RegistryItem item)
    {
        RemoveOrdered(orderedItems, item);

        if (itemsBySourceMod.TryGetValue(item.SourceModId, out List<RegistryItem>? sourceItems))
        {
            RemoveOrdered(sourceItems, item);
            if (sourceItems.Count == 0)
                itemsBySourceMod.Remove(item.SourceModId);
        }

        foreach (string tag in itemsByTag.Keys.ToList())
        {
            RegistryItem[] members = itemsByTag[tag];
            RegistryItem[] filtered = members
                .Where(member => !string.Equals(member.ToString(), item.ToString(), StringComparison.OrdinalIgnoreCase))
                .ToArray();

            if (filtered.Length == members.Length)
                continue;

            if (filtered.Length == 0)
                itemsByTag.Remove(tag);
            else
                itemsByTag[tag] = filtered;
        }
    }
}
