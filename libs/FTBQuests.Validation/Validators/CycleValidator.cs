using System;
using System.Collections.Generic;
using System.Linq;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuests.Validation.Validators;

/// <summary>
/// Detects dependency cycles across quests.
/// </summary>
public sealed class CycleValidator
{
    /// <summary>
    /// Validates chapters for dependency cycles.
    /// </summary>
    /// <param name="chapters">The chapters to inspect.</param>
    /// <returns>All detected dependency cycles.</returns>
    /// <exception cref="ArgumentNullException">Thrown when <paramref name="chapters"/> is <c>null</c>.</exception>
    public IReadOnlyList<CycleValidationResult> Validate(IEnumerable<Chapter> chapters)
    {
        if (chapters is null)
        {
            throw new ArgumentNullException(nameof(chapters));
        }

        var questLookup = new Dictionary<Guid, QuestContext>();
        var visitOrder = new List<Guid>();

        foreach (var chapter in chapters)
        {
            if (chapter is null)
            {
                continue;
            }

            foreach (var quest in chapter.Quests ?? Enumerable.Empty<Quest>())
            {
                if (quest is null)
                {
                    continue;
                }

                questLookup[quest.Id] = new QuestContext(chapter, quest);
                visitOrder.Add(quest.Id);
            }
        }

        var visitStates = new Dictionary<Guid, VisitState>();
        var stack = new List<Guid>();
        var seenCycles = new HashSet<string>(StringComparer.Ordinal);
        var results = new List<CycleValidationResult>();

        foreach (var questId in visitOrder)
        {
            if (!visitStates.ContainsKey(questId))
            {
                DepthFirstSearch(questId);
            }
        }

        return results;

        void DepthFirstSearch(Guid questId)
        {
            visitStates[questId] = VisitState.Gray;
            stack.Add(questId);

            foreach (var dependencyId in questLookup[questId].Quest.Dependencies)
            {
                if (!questLookup.ContainsKey(dependencyId))
                {
                    continue;
                }

                if (!visitStates.TryGetValue(dependencyId, out var state))
                {
                    DepthFirstSearch(dependencyId);
                }
                else if (state == VisitState.Gray)
                {
                    var cyclePath = ExtractCyclePath(dependencyId, questId);
                    var key = BuildCycleKey(cyclePath);

                    if (seenCycles.Add(key))
                    {
                        var nodes = cyclePath
                            .Select(id =>
                            {
                                var context = questLookup[id];
                                return new CyclePathNode(context.Chapter.Title, context.Quest.Title, id);
                            })
                            .ToList();

                        results.Add(new CycleValidationResult(nodes));
                    }
                }

            }

            stack.RemoveAt(stack.Count - 1);
            visitStates[questId] = VisitState.Black;
        }

        List<Guid> ExtractCyclePath(Guid startId, Guid currentId)
        {
            var startIndex = stack.IndexOf(startId);
            if (startIndex < 0)
            {
                return new List<Guid> { startId, currentId };
            }

            var cycle = new List<Guid>();
            for (var i = startIndex; i < stack.Count; i++)
            {
                cycle.Add(stack[i]);
            }

            cycle.Add(startId);
            return cycle;
        }

        static string BuildCycleKey(IReadOnlyList<Guid> cycle)
        {
            if (cycle.Count == 0)
            {
                return string.Empty;
            }

            var uniqueNodes = cycle.Take(cycle.Count - 1).ToArray();
            if (uniqueNodes.Length == 0)
            {
                uniqueNodes = new[] { cycle[0] };
            }

            var best = ToKey(uniqueNodes, 0);
            for (var offset = 1; offset < uniqueNodes.Length; offset++)
            {
                var candidate = ToKey(uniqueNodes, offset);
                if (string.CompareOrdinal(candidate, best) < 0)
                {
                    best = candidate;
                }
            }

            return best;

            static string ToKey(Guid[] values, int offset)
            {
                var span = new Guid[values.Length];
                for (var i = 0; i < values.Length; i++)
                {
                    span[i] = values[(i + offset) % values.Length];
                }

                return string.Join("->", span.Select(v => v.ToString("D"))) + "->" + span[0].ToString("D");
            }
        }
    }

    private enum VisitState
    {
        White,
        Gray,
        Black,
    }

    private readonly record struct QuestContext(Chapter Chapter, Quest Quest);
}

/// <summary>
/// Represents a node in a dependency cycle.
/// </summary>
public sealed record CyclePathNode(string ChapterTitle, string QuestTitle, Guid QuestId);

/// <summary>
/// Represents a detected dependency cycle.
/// </summary>
public sealed class CycleValidationResult
{
    public CycleValidationResult(IReadOnlyList<CyclePathNode> path)
    {
        Path = path ?? throw new ArgumentNullException(nameof(path));
    }

    public IReadOnlyList<CyclePathNode> Path { get; }
}
