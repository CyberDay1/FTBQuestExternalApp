using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using FTBQuests.Assets;
using FTBQuests.Codecs.Model;
using FTBQuests.Core.Model;
using FTBQuests.Registry.Model;

namespace FTBQuests.Registry;

/// <summary>
/// Provides in-memory lookups over registry entries imported from the probe output.
/// </summary>
public sealed class RegistryDatabase
{
    private static readonly RegistryItem[] EmptyItems = Array.Empty<RegistryItem>();

    private readonly Dictionary<string, RegistryItem> itemsById;
    private readonly Dictionary<string, RegistryItem[]> itemsByTag;
    private readonly Dictionary<string, List<RegistryItem>> itemsBySourceMod;
    private readonly List<RegistryItem> orderedItems;
    private readonly IReadOnlyDictionary<string, IReadOnlyCollection<string>> tagMembership;

    private static readonly IComparer<RegistryItem> IdentifierComparer =
        Comparer<RegistryItem>.Create(
            static (left, right) => string.CompareOrdinal(left.ToString(), right.ToString())
        );

    public RegistryDatabase(
        IEnumerable<RegistryItem> items,
        IReadOnlyDictionary<string, IReadOnlyCollection<string>> tagMembership)
    {
        ArgumentNullException.ThrowIfNull(items);
        ArgumentNullException.ThrowIfNull(tagMembership);

        itemsById = new Dictionary<string, RegistryItem>(StringComparer.OrdinalIgnoreCase);
        var bySource = new Dictionary<string, List<RegistryItem>>(StringComparer.OrdinalIgnoreCase);

        foreach (var item in items)
        {
            itemsById[item.ToString()] = item;

            if (!bySource.TryGetValue(item.SourceModId, out var sourceItems))
            {
                sourceItems = new List<RegistryItem>();
                bySource[item.SourceModId] = sourceItems;
            }

            sourceItems.Add(item);
        }

        itemsBySourceMod = new Dictionary<string, List<RegistryItem>>(StringComparer.OrdinalIgnoreCase);
        foreach (var (sourceMod, sourceItems) in bySource)
        {
            sourceItems.Sort(IdentifierComparer);
            itemsBySourceMod[sourceMod] = sourceItems;
        }

        var normalizedMembership = new Dictionary<string, IReadOnlyCollection<string>>(StringComparer.OrdinalIgnoreCase);
        foreach (var (tag, identifiers) in tagMembership)
        {
            if (string.IsNullOrWhiteSpace(tag))
                continue;

            var normalizedTag = tag.Trim();
            var source = identifiers ?? Array.Empty<string>();
            var normalizedIdentifiers = source
                .Where(static id => !string.IsNullOrWhiteSpace(id))
                .Select(static id => id.Trim())
                .ToArray();

            normalizedMembership[normalizedTag] =
                normalizedIdentifiers.Length == 0 ? Array.Empty<string>() : normalizedIdentifiers;
        }

        this.tagMembership = new ReadOnlyDictionary<string, IReadOnlyCollection<string>>(normalizedMembership);

        itemsByTag = new Dictionary<string, RegistryItem[]>(StringComparer.OrdinalIgnoreCase);
        foreach (var (tag, identifiers) in normalizedMembership)
        {
            var resolved = identifiers
                .Select(id => TryGetByIdentifier(id, out var match) ? match : null)
                .Where(static item => item is not null)
                .Select(static item => item!)
                .Distinct()
                .OrderBy(static item => item.ToString(), StringComparer.Ordinal)
                .ToArray();

            itemsByTag[tag] = resolved;
        }

        orderedItems = itemsById.Values
            .OrderBy(static item => item.ToString(), StringComparer.Ordinal)
            .ToList();
    }

    public IReadOnlyCollection<RegistryItem> Items => orderedItems;
    public IReadOnlyDictionary<string, IReadOnlyCollection<string>> TagMembership => tagMembership;

    public bool TryGetByIdentifier(string identifier, out RegistryItem? item)
    {
        ArgumentException.ThrowIfNullOrEmpty(identifier);
        return itemsById.TryGetValue(identifier, out item);
    }

    public IReadOnlyList<RegistryItem> GetByTag(string tag)
    {
        ArgumentException.ThrowIfNullOrEmpty(tag);
        return itemsByTag.TryGetValue(tag, out var items) ? items : EmptyItems;
    }

    public IReadOnlyList<RegistryItem> GetBySourceModId(string sourceModId)
    {
        ArgumentException.ThrowIfNullOrEmpty(sourceModId);
        return itemsBySourceMod.TryGetValue(sourceModId, out var sourceItems)
            ? sourceItems
            : EmptyItems;
    }

    public IReadOnlyList<RegistryItem> GetItemsByMod(string modId) => GetBySourceModId(modId);

    public IReadOnlyCollection<string> GetModIdentifiers() =>
        itemsBySourceMod.Keys.OrderBy(static id => id, StringComparer.OrdinalIgnoreCase).ToList();

    public bool RemoveItem(Identifier id)
    {
        ArgumentNullException.ThrowIfNull(id);
        if (string.IsNullOrWhiteSpace(id.ToString()))
            throw new ArgumentException("Identifier cannot be empty.", nameof(id));

        if (!itemsById.Remove(id.ToString(), out var item))
            return false;

        RemoveItemInternal(item);
        return true;
    }

    public int RemoveItemsByMod(string modId)
    {
        ArgumentException.ThrowIfNullOrEmpty(modId);

        if (!itemsBySourceMod.TryGetValue(modId, out var modItems) || modItems.Count == 0)
            return 0;

        var snapshot = modItems.ToArray();
        var removed = 0;

        foreach (var item in snapshot)
        {
            if (!itemsById.Remove(item.ToString()))
                continue;

            RemoveItemInternal(item);
            removed++;
        }

        return removed;
    }

    public void AddIfMissing(RegistryItem item)
    {
        ArgumentNullException.ThrowIfNull(item);

        if (itemsById.ContainsKey(item.ToString()))
            return;

        itemsById[item.ToString()] = item;
        InsertOrdered(orderedItems, item);

        if (!itemsBySourceMod.TryGetValue(item.SourceModId, out var sourceItems))
        {
            sourceItems = new List<RegistryItem>();
            itemsBySourceMod[item.SourceModId] = sourceItems;
        }

        InsertOrdered(sourceItems, item);
    }

    private static void InsertOrdered(List<RegistryItem> list, RegistryItem item)
    {
        var index = list.BinarySearch(item, IdentifierComparer);
        if (index < 0)
            index = ~index;

        list.Insert(index, item);
    }

    private static void RemoveOrdered(List<RegistryItem> list, RegistryItem item)
    {
        var index = list.BinarySearch(item, IdentifierComparer);
        if (index >= 0)
            list.RemoveAt(index);
    }

    private void RemoveItemInternal(RegistryItem item)
    {
        RemoveOrdered(orderedItems, item);

        if (itemsBySourceMod.TryGetValue(item.SourceModId, out var sourceItems))
        {
            RemoveOrdered(sourceItems, item);
            if (sourceItems.Count == 0)
                itemsBySourceMod.Remove(item.SourceModId);
        }

        foreach (var tag in itemsByTag.Keys.ToList())
        {
            var members = itemsByTag[tag];
            var filtered = members
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
