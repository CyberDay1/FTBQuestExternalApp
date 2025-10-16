using FTBQuests.Validation;
using FTBQuests.Assets;
namespace FTBQuests.Registry;

/// <summary>
/// Handles codec registration for quest data serialization.
/// </summary>
public sealed class CodecRegistry
{
    public static CodecRegistry Instance { get; } = new();

    private CodecRegistry() { }

    /// <summary>
    /// Registers all available codecs.
    /// </summary>
    public void RegisterAll()
    {
        // Placeholder implementation
    }
}
