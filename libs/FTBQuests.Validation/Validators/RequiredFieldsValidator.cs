using System;
using System.Collections.Generic;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuests.Validation.Validators;

/// <summary>
/// Ensures that critical quest fields are populated.
/// </summary>
public sealed class RequiredFieldsValidator : IValidator
{
    /// <inheritdoc />
    public IEnumerable<ValidationIssue> Validate(QuestPack questPack)
    {
        ArgumentNullException.ThrowIfNull(questPack);

        var issues = new List<ValidationIssue>();

        for (var chapterIndex = 0; chapterIndex < questPack.Chapters.Count; chapterIndex++)
        {
            var chapter = questPack.Chapters[chapterIndex];
            if (chapter is null)
            {
                issues.Add(new ValidationIssue(
                    ValidationSeverity.Error,
                    $"chapters[{chapterIndex}]",
                    "Chapter entry is null.",
                    "REQ_CHAPTER_NULL"));
                continue;
            }

            if (chapter.Id == Guid.Empty)
            {
                issues.Add(new ValidationIssue(
                    ValidationSeverity.Error,
                    $"chapters[{chapterIndex}].id",
                    "Chapter must have a non-empty identifier.",
                    "REQ_CHAPTER_ID"));
            }

            if (string.IsNullOrWhiteSpace(chapter.Title))
            {
                issues.Add(new ValidationIssue(
                    ValidationSeverity.Error,
                    $"chapters[{chapterIndex}].title",
                    "Chapter must have a title.",
                    "REQ_CHAPTER_TITLE"));
            }

            if (chapter.Quests is null)
            {
                issues.Add(new ValidationIssue(
                    ValidationSeverity.Error,
                    $"chapters[{chapterIndex}].quests",
                    "Chapter quests collection is missing.",
                    "REQ_CHAPTER_QUESTS"));
                continue;
            }

            for (var questIndex = 0; questIndex < chapter.Quests.Count; questIndex++)
            {
                var quest = chapter.Quests[questIndex];
                if (quest is null)
                {
                    issues.Add(new ValidationIssue(
                        ValidationSeverity.Error,
                        $"chapters[{chapterIndex}].quests[{questIndex}]",
                        "Quest entry is null.",
                        "REQ_QUEST_NULL"));
                    continue;
                }

                if (quest.Id == Guid.Empty)
                {
                    issues.Add(new ValidationIssue(
                        ValidationSeverity.Error,
                        $"chapters[{chapterIndex}].quests[{questIndex}].id",
                        "Quest must have a non-empty identifier.",
                        "REQ_QUEST_ID"));
                }

                if (string.IsNullOrWhiteSpace(quest.Title))
                {
                    issues.Add(new ValidationIssue(
                        ValidationSeverity.Error,
                        $"chapters[{chapterIndex}].quests[{questIndex}].title",
                        "Quest must have a title.",
                        "REQ_QUEST_TITLE"));
                }
            }
        }

        return issues;
    }
}
