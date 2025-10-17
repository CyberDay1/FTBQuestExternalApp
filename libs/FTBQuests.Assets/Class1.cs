using System;

namespace FTBQuests.Assets
{
    /// <summary>
    /// Provides access to quest-related assets. Registry is held as an opaque dependency
    /// to avoid cross-project cycles.
    /// </summary>
    public sealed class QuestAssetProvider
    {
        public QuestAssetProvider(object registry)
        {
            Registry = registry ?? throw new ArgumentNullException(nameof(registry));
        }

        public object Registry { get; }
    }
}
