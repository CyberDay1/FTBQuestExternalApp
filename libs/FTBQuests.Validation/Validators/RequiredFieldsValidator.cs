// <copyright file="RequiredFieldsValidator.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>
using System;
using System.Collections.Generic;

using FTBQuests.Codecs.Model;

using FTBQuests.Assets;

namespace FTBQuests.Validation.Validators;

/// <summary>
/// Ensures that critical quest fields are populated.
/// </summary>
public sealed class RequiredFieldsValidator : IValidator
{
    /// <inheritdoc />
    public IEnumerable<ValidationIssue> Validate(FTBQuests.IO.QuestPack questPack)
    {
        ArgumentNullException.ThrowIfNull(FTBQuests.IO.QuestPack);

        var issues = new List<ValidationIssue>();

        for (var chapterIndex = 0; chapterIndex < FTBQuests.IO.QuestPack.Chapters.Count; chapterIndex++)
        {
            var chapter = FTBQuests.IO.QuestPack.Chapters[chapterIndex];
            if (chapter is null)
            {
                issues.Add(new ValidationIssue(
                    ValidationSeverity.Error,
                    $"chapters[{chapterIndex}]",
                    "Chapter entry is null.",
                    "REQ_CHAPTER_NULL"));
                continue;
            }

            if (chapter.Id == 0)
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

                if (quest.Id == 0)
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
