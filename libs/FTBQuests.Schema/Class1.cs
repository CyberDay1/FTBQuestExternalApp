using FTBQuests.Codecs;

namespace FTBQuests.Schema;

/// <summary>
/// Provides schema definition helpers for quests.
/// </summary>
public sealed class QuestSchemaBuilder
{
    /// <summary>
    /// Creates a schema builder seeded with the default codec registry.
    /// </summary>
    /// <returns>A schema builder.</returns>
    public static QuestSchemaBuilder CreateDefault() => new(CodecRegistry.Instance);

    /// <summary>
    /// Initializes a new instance of the <see cref="QuestSchemaBuilder"/> class.
    /// </summary>
    /// <param name="codecRegistry">The codec registry used to describe data types.</param>
    public QuestSchemaBuilder(CodecRegistry codecRegistry)
    {
        CodecRegistry = codecRegistry ?? throw new ArgumentNullException(nameof(codecRegistry));
    }

    /// <summary>
    /// Gets the codec registry used by this builder.
    /// </summary>
    public CodecRegistry CodecRegistry { get; }
}
