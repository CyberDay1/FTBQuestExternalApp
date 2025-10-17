namespace FTBQuests.Codecs;

/// <summary>
/// Provides registration and management for all available codecs used in quest serialization.
/// </summary>
public sealed class CodecRegistry
{
    public static CodecRegistry Instance { get; } = new();

    private CodecRegistry()
    {
    }

    /// <summary>
    /// Registers all codecs.
    /// </summary>
    public void RegisterAll()
    {
        // Placeholder for codec registration logic.
        // Actual codec initialization will be filled by Codex during schema linking.
    }
}
