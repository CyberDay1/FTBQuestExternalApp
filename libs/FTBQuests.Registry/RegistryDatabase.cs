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
    private readonly Dictionary<string, RegistryItem[]> itemsBySourceMod;

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
            itemsById[item.Id] = item;

            if (!bySource.TryGetValue(item.SourceModId, out List<RegistryItem>? sourceItems))
            {
                sourceItems = new List<RegistryItem>();
                bySource[item.SourceModId] = sourceItems;
            }

            sourceItems.Add(item);
        }

        itemsBySourceMod = new Dictionary<string, RegistryItem[]>(StringComparer.OrdinalIgnoreCase);
        foreach ((string sourceMod, List<RegistryItem> sourceItems) in bySource)
        {
            RegistryItem[] ordered = sourceItems
                .OrderBy(static item => item.Id, StringComparer.Ordinal)
                .ToArray();
            itemsBySourceMod[sourceMod] = ordered;
        }

        itemsByTag = new Dictionary<string, RegistryItem[]>(StringComparer.OrdinalIgnoreCase);
        foreach ((string tag, IReadOnlyCollection<string> identifiers) in tagMembership)
        {
            RegistryItem[] resolved = identifiers
                .Select(identifier => TryGetByIdentifier(identifier, out RegistryItem? match) ? match : null)
                .Where(static item => item is not null)
                .Select(static item => item!)
                .Distinct()
                .OrderBy(static item => item.Id, StringComparer.Ordinal)
                .ToArray();

            itemsByTag[tag] = resolved;
        }

        Items = itemsById.Values
            .OrderBy(static item => item.Id, StringComparer.Ordinal)
            .ToArray();
    }

    /// <summary>
    /// Gets a read-only view of all registry items.
    /// </summary>
    public IReadOnlyCollection<RegistryItem> Items { get; }

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
        return itemsBySourceMod.TryGetValue(sourceModId, out RegistryItem[]? items) ? items : EmptyItems;
    }
}
