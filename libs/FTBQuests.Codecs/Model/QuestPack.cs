using System.Collections.Generic;

namespace FTBQuestExternalApp.Codecs.Model;

/// <summary>
/// Represents the root quest pack containing all chapters and quests.
/// </summary>
public class QuestPack : IExtraAware
{
    private readonly List<Chapter> chapters = new();

    /// <summary>
    /// Gets the collection of chapters included in the pack.
    /// </summary>
    public IReadOnlyList<Chapter> Chapters => chapters;

    /// <summary>
    /// Gets the bag of additional metadata preserved during serialization.
    /// </summary>
    public PropertyBag Extra { get; } = new();

    /// <summary>
    /// Adds a chapter to the quest pack.
    /// </summary>
    /// <param name="chapter">The chapter to add.</param>
    public void AddChapter(Chapter chapter)
    {
        chapters.Add(chapter);
    }

    /// <summary>
    /// Removes all chapters from the quest pack.
    /// </summary>
    public void ClearChapters()
    {
        chapters.Clear();
    }
}
