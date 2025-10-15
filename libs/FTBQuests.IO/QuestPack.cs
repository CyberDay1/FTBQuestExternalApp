using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using FTBQuestExternalApp.Codecs.Model;
using Newtonsoft.Json.Linq;

namespace FTBQuests.IO;

public class QuestPack
{
    private readonly List<Chapter> chapters = new();
    private readonly List<string> metadataOrder = new();
    private readonly Dictionary<Chapter, string> chapterPaths = new(ReferenceEqualityComparer.Instance);

    public IList<Chapter> Chapters => chapters;

    public PropertyBag Metadata { get; } = new();

    internal IReadOnlyDictionary<Chapter, string> ChapterPaths => new ReadOnlyDictionary<Chapter, string>(chapterPaths);

    internal IReadOnlyList<string> MetadataOrder => metadataOrder.AsReadOnly();

    internal void AddChapter(Chapter chapter, string relativePath)
    {
        chapters.Add(chapter);
        chapterPaths[chapter] = relativePath;
    }

    internal void SetChapterPath(Chapter chapter, string relativePath)
    {
        chapterPaths[chapter] = relativePath;
    }

    internal string? GetChapterPath(Chapter chapter)
    {
        return chapterPaths.TryGetValue(chapter, out var path) ? path : null;
    }

    internal void SetMetadata(string key, JToken value)
    {
        Metadata.Add(key, value);
        if (!metadataOrder.Contains(key))
        {
            metadataOrder.Add(key);
        }
    }

    internal void SetMetadataOrder(IEnumerable<string> order)
    {
        metadataOrder.Clear();
        metadataOrder.AddRange(order);
    }

    internal IEnumerable<string> EnumerateMetadataKeysInOrder()
    {
        var seen = new HashSet<string>(metadataOrder.Count, StringComparer.Ordinal);

        foreach (var key in metadataOrder)
        {
            if (Metadata.Extra.ContainsKey(key) && seen.Add(key))
            {
                yield return key;
            }
        }

        foreach (var key in Metadata.Extra.Keys)
        {
            if (seen.Add(key))
            {
                yield return key;
            }
        }
    }
}
