using FTBQuests.Core.Model;
using FTBQuests.Assets;
namespace FTBQuests.Registry;

/// <summary>
/// Provides quest registry management for asset and codec registration.
/// </summary>
public sealed class QuestRegistry
{
    public static QuestRegistry Instance { get; } = new();

    private QuestRegistry() { }

    /// <summary>
    /// Performs quest registry initialization.
    /// </summary>
    public void RegisterAll()
    {
        // Placeholder logic; Codex will populate actual registry entries
    }
}

