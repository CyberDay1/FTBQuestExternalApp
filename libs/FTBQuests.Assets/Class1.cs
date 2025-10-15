using FTBQuests.Registry;

namespace FTBQuests.Assets;

/// <summary>
/// Provides access to quest-related assets that are backed by the registry.
/// </summary>
public sealed class QuestAssetProvider
{
    /// <summary>
    /// Initializes a new instance of the <see cref="QuestAssetProvider"/> class.
    /// </summary>
    /// <param name="registry">The registry used to resolve assets.</param>
    public QuestAssetProvider(QuestRegistry registry)
    {
        Registry = registry ?? throw new ArgumentNullException(nameof(registry));
    }

    /// <summary>
    /// Gets the registry used by the provider.
    /// </summary>
    public QuestRegistry Registry { get; }
}
