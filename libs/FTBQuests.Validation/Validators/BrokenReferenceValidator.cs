using System;
using System.Collections.Generic;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuests.Validation.Validators;

/// <summary>
/// Validates that quest dependency references point to existing quests.
/// </summary>
public sealed class BrokenReferenceValidator : IValidator
{
    /// <inheritdoc />
    public IEnumerable<ValidationIssue> Validate(QuestPack questPack)
    {
        ArgumentNullException.ThrowIfNull(questPack);

        var issues = new List<ValidationIssue>();
        var knownQuestIds = new HashSet<long>();

        for (var chapterIndex = 0; chapterIndex < questPack.Chapters.Count; chapterIndex++)
        {
            var chapter = questPack.Chapters[chapterIndex];
            if (chapter is null)
            {
                continue;
            }

            for (var questIndex = 0; questIndex < chapter.Quests.Count; questIndex++)
            {
                var quest = chapter.Quests[questIndex];
                if (quest is null)
                {
                    continue;
                }

                if (quest.Id != 0)
                {
                    knownQuestIds.Add(quest.Id);
                }
            }
        }

        for (var chapterIndex = 0; chapterIndex < questPack.Chapters.Count; chapterIndex++)
        {
            var chapter = questPack.Chapters[chapterIndex];
            if (chapter is null)
            {
                continue;
            }

            for (var questIndex = 0; questIndex < chapter.Quests.Count; questIndex++)
            {
                var quest = chapter.Quests[questIndex];
                if (quest is null)
                {
                    continue;
                }

                for (var dependencyIndex = 0; dependencyIndex < quest.Dependencies.Count; dependencyIndex++)
                {
                    var dependency = quest.Dependencies[dependencyIndex];
                    if (dependency == 0)
                    {
                        issues.Add(new ValidationIssue(
                            ValidationSeverity.Error,
                            $"chapters[{chapterIndex}].quests[{questIndex}].dependencies[{dependencyIndex}]",
                            "Quest dependency must reference a non-empty quest identifier.",
                            "BROKEN_DEPENDENCY_EMPTY"));
                        continue;
                    }

                    if (!knownQuestIds.Contains(dependency))
                    {
                        issues.Add(new ValidationIssue(
                            ValidationSeverity.Error,
                            $"chapters[{chapterIndex}].quests[{questIndex}].dependencies[{dependencyIndex}]",
                            "Quest dependency references a quest that does not exist in the pack.",
                            "BROKEN_DEPENDENCY_MISSING"));
                    }
                }
            }
        }

        return issues;
    }
}
