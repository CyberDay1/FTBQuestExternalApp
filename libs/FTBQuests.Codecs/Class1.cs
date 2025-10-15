namespace FTBQuests.Codecs;

/// <summary>
/// Provides a central point for codec-related helpers.
/// </summary>
public sealed class CodecRegistry
{
    /// <summary>
    /// Gets the singleton instance of the registry.
    /// </summary>
    public static CodecRegistry Instance { get; } = new();

    private CodecRegistry()
    {
    }
}
