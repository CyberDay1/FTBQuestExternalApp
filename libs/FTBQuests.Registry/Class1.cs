namespace FTBQuests.Registry;

/// <summary>
/// Represents a simple registry used to look up quest assets or codecs.
/// </summary>
public sealed class QuestRegistry
{
    private readonly Dictionary<string, object> entries = new(StringComparer.OrdinalIgnoreCase);

    /// <summary>
    /// Registers an entry with the provided key.
    /// </summary>
    /// <param name="key">The unique key.</param>
    /// <param name="value">The value to store.</param>
    public void Register(string key, object value)
    {
        ArgumentException.ThrowIfNullOrEmpty(key);
        ArgumentNullException.ThrowIfNull(value);
        entries[key] = value;
    }

    /// <summary>
    /// Tries to get an entry from the registry.
    /// </summary>
    /// <param name="key">The entry key.</param>
    /// <param name="value">The located value.</param>
    /// <returns><see langword="true"/> when found.</returns>
    public bool TryGet(string key, out object? value)
    {
        ArgumentException.ThrowIfNullOrEmpty(key);
        return entries.TryGetValue(key, out value);
    }
}
