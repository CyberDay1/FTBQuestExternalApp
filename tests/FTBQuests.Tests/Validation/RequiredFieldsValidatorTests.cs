using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="RequiredFieldsValidatorTests.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System.Linq;
using FTBQuests.Codecs.Model;
using FTBQuests.Validation.Validators;
using Xunit;

namespace FTBQuests.Tests.Validation;

public class RequiredFieldsValidatorTests
{
    [Fact]
    public void Validate_ReturnsIssuesForMissingCriticalFields()
    {
        var questWithoutTitle = new Quest
        {
            Id = 101,
        };

        var questWithMissingId = new Quest
        {
            Title = "Missing Id",
        };

        var chapter = new Chapter();
        chapter.AddQuest(questWithoutTitle);
        chapter.AddQuest(questWithMissingId);

        var pack = new FTBQuests.IO.QuestPack();
        pack.AddChapter(chapter);

        var validator = new RequiredFieldsValidator();

        var issues = validator.Validate(pack).ToList();

        Assert.Collection(
            issues.OrderBy(i => i.Code).ThenBy(i => i.Path),
            issue =>
            {
                Assert.Equal("REQ_CHAPTER_ID", issue.Code);
                Assert.Equal("chapters[0].id", issue.Path);
            },
            issue =>
            {
                Assert.Equal("REQ_CHAPTER_TITLE", issue.Code);
                Assert.Equal("chapters[0].title", issue.Path);
            },
            issue =>
            {
                Assert.Equal("REQ_QUEST_TITLE", issue.Code);
                Assert.Equal("chapters[0].quests[0].title", issue.Path);
            },
            issue =>
            {
                Assert.Equal("REQ_QUEST_ID", issue.Code);
                Assert.Equal("chapters[0].quests[1].id", issue.Path);
            });
    }
}

