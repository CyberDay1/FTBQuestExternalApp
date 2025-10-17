using FTBQuests.Core.Model;
using FTBQuests.Assets;
// <copyright file="RegistryDatabase.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using FTBQuests.Codecs.Model;
using FTBQuests.Registry.Model;

namespace FTBQuests.Registry;

/// <summary>
/// Provides in-memory lookups over registry entries imported from the probe output.
/// </summary>
public sealed class RegistryDatabase
{
    private static readonly RegistryItem[] EmptyItems = Array.Empty<RegistryItem>();

    private readonly Dictionary<string, RegistryItem> itemsById;
    private static readonly IComparer<RegistryItem> IdentifierComparer = Comparer<RegistryItem>.Create(
        static (left, right) => string.CompareOrdinal(left.ToString(), right.ToString()));

    private readonly Dictionary<string, RegistryItem[]> itemsByTag;
    private readonly IReadOnlyDictionary<string, IReadOnlyCollection<string>> tagMembership;
    private readonly Dictionary<string, List<RegistryItem>> itemsBySourceMod;
    private readonly List<RegistryItem> orderedItems;

    /// <summary>
    /// Initializes a new instance of the <see cref="RegistryDatabase"/> class.
    /// </summary>
    /// <param name="items">The registry items to index.</param>
    /// <param name="tagMembership">The mapping between tag identifiers and registry item identifiers.</param>
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
        foreach ((string sourceMod, List<RegistryItem> sourceItems) in bySource)
        {
            sourceItems.Sort(IdentifierComparer);
            itemsBySourceMod[sourceMod] = sourceItems;
        }

        var normalizedMembership = new Dictionary<string, IReadOnlyCollection<string>>(StringComparer.OrdinalIgnoreCase);
        foreach ((string tag, IReadOnlyCollection<string> identifiers) in tagMembership)
        {
            if (string.IsNullOrWhiteSpace(tag))
            {
                continue;
            }

            string normalizedTag = tag.Trim();
            IEnumerable<string> source = identifiers ?? Array.Empty<string>();
            string[] normalizedIdentifiers = source
                .Where(static identifier => !string.IsNullOrWhiteSpace(identifier))
                .Select(static identifier => identifier.Trim())
                .ToArray();

            normalizedMembership[normalizedTag] = normalizedIdentifiers.Length == 0
                ? Array.Empty<string>()
                : normalizedIdentifiers;
        }

        this.tagMembership = new ReadOnlyDictionary<string, IReadOnlyCollection<string>>(normalizedMembership);

        itemsByTag = new Dictionary<string, RegistryItem[]>(StringComparer.OrdinalIgnoreCase);
        foreach ((string tag, IReadOnlyCollection<string> identifiers) in normalizedMembership)
        {
            RegistryItem[] resolved = identifiers
                .Select(identifier => TryGetByIdentifier(identifier, out RegistryItem? match) ? match : null)
                .Where(static item => item is not null)
                .Select(static item => item!)
                .Distinct()
                .OrderBy(static item => item.ToString(), StringComparer.Ordinal)
                .ToArray();

            itemsByTag[tag] = resolved;
        }

        orderedItems = itemsById.Keys
            .OrderBy(static item => item.ToString(), StringComparer.Ordinal)
            .ToList();
    }

    /// <summary>
    /// Gets a read-only view of all registry items.
    /// </summary>
    public IReadOnlyCollection<RegistryItem> Items => orderedItems;

    /// <summary>
    /// Tries to fetch an item by its identifier.
    /// </summary>
    /// <param name="identifier">The fully qualified identifier.</param>
    /// <param name="item">The located item, when found.</param>
    /// <returns><see langword="true"/> when the item is present.</returns>
    public bool TryGetByIdentifier(string identifier, out RegistryItem? item)
    {
        ArgumentException.ThrowIfNullOrEmpty(identifier);
        return itemsById.TryGetValue(identifier, out item);
    }

    /// <summary>
    /// Retrieves the items that belong to a specific tag.
    /// </summary>
    /// <param name="tag">The tag identifier.</param>
    /// <returns>A read-only list of registry items.</returns>
    public IReadOnlyList<RegistryItem> GetByTag(string tag)
    {
        ArgumentException.ThrowIfNullOrEmpty(tag);
        return itemsByTag.TryGetValue(tag, out RegistryItem[]? items) ? items : EmptyItems;
    }

    /// <summary>
    /// Retrieves the items produced by a specific mod identifier.
    /// </summary>
    /// <param name="sourceModId">The mod identifier.</param>
    /// <returns>A read-only list of registry items.</returns>
    public IReadOnlyList<RegistryItem> GetBySourceModId(string sourceModId)
    {
        ArgumentException.ThrowIfNullOrEmpty(sourceModId);
        return itemsBySourceMod.TryGetValue(sourceModId, out List<RegistryItem>? sourceItems) ? sourceItems : EmptyItems;
    }

    /// <summary>
    /// Retrieves the items provided by the supplied mod identifier.
    /// </summary>
    /// <param name="modId">The originating mod identifier.</param>
    /// <returns>A read-only list of registry items.</returns>
    public IReadOnlyList<RegistryItem> GetItemsByMod(string modId) => GetBySourceModId(modId);

    /// <summary>
    /// Gets the set of known source mod identifiers.
    /// </summary>
    public IReadOnlyCollection<string> GetModIdentifiers() => itemsBySourceMod.Keys
        .OrderBy(static id => id, StringComparer.OrdinalIgnoreCase)
        .ToList();

    /// <summary>
    /// Removes the item associated with the supplied identifier when present.
    /// </summary>
    /// <param name="id">The fully qualified identifier.</param>
    /// <returns><see langword="true"/> when an entry was removed.</returns>
    public bool RemoveItem(Identifier id)
    {
        if (string.IsNullOrWhiteSpace(id.ToString()))
        {
            throw new ArgumentException("Identifier cannot be empty.", nameof(id));
        }

        if (!itemsById.Remove(id.ToString(), out RegistryItem? item))
        {
            return false;
        }

        RemoveItemInternal(item);
        return true;
    }

    /// <summary>
    /// Removes all items contributed by the specified mod identifier.
    /// </summary>
    /// <param name="modId">The source mod identifier.</param>
    /// <returns>The number of removed items.</returns>
    public int RemoveItemsByMod(string modId)
    {
        ArgumentException.ThrowIfNullOrEmpty(modId);

        if (!itemsBySourceMod.TryGetValue(modId, out List<RegistryItem>? modItems) || modItems.Count == 0)
        {
            return 0;
        }

        var snapshot = modItems.ToArray();
        int removed = 0;
        foreach (RegistryItem item in snapshot)
        {
            if (!itemsById.Remove(item.ToString()))
            {
                continue;
            }

            RemoveItemInternal(item);
            removed++;
        }

        return removed;
    }
    /// Gets the normalized tag membership captured during construction.
    /// </summary>
    public IReadOnlyDictionary<string, IReadOnlyCollection<string>> TagMembership => this.tagMembership;

    /// <summary>
    /// Adds the specified item when it is not already tracked.
    /// </summary>
    /// <param name="item">The item to register.</param>
    public void AddIfMissing(RegistryItem item)
    {
        ArgumentNullException.ThrowIfNull(item);

        if (itemsById.ContainsKey(item.ToString()))
        {
            return;
        }

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
        {
            index = ~index;
        }

        list.Insert(index, item);
    }

    private static void RemoveOrdered(List<RegistryItem> list, RegistryItem item)
    {
        int index = list.BinarySearch(item, IdentifierComparer);
        if (index >= 0)
        {
            list.RemoveAt(index);
        }
    }

    private void RemoveItemInternal(RegistryItem item)
    {
        RemoveOrdered(orderedItems, item);

        if (itemsBySourceMod.TryGetValue(item.SourceModId, out List<RegistryItem>? sourceItems))
        {
            RemoveOrdered(sourceItems, item);
            if (sourceItems.Count == 0)
            {
                itemsBySourceMod.Remove(item.SourceModId);
            }
        }

        foreach (string tag in itemsByTag.Keys.ToList())
        {
            RegistryItem[] members = itemsByTag[tag];
            RegistryItem[] filtered = members
                .Where(member => !string.Equals(member.ToString(), item.ToString(), StringComparison.OrdinalIgnoreCase))
                .ToArray();

            if (filtered.Length == members.Length)
            {
                continue;
            }

            if (filtered.Length == 0)
            {
                itemsByTag.Remove(tag);
            }
            else
            {
                itemsByTag[tag] = filtered;
            }
        }
    }
}





