using System;
using System.Linq;
using FTBQuestExternalApp.Codecs.Model;
using FTBQuests.Validation.Validators;
using Xunit;

namespace FTBQuests.Tests.Validation;

public class BrokenReferenceValidatorTests
{
    [Fact]
    public void Validate_FlagsMissingQuestDependencies()
    {
        var targetQuest = new Quest
        {
            Id = Guid.Parse("11111111-1111-1111-1111-111111111111"),
            Title = "Existing Quest",
        };

        var questWithMissingDependency = new Quest
        {
            Id = Guid.Parse("22222222-2222-2222-2222-222222222222"),
            Title = "Needs Friend",
        };

        questWithMissingDependency.Dependencies.Add(Guid.Parse("33333333-3333-3333-3333-333333333333"));
        questWithMissingDependency.Dependencies.Add(Guid.Empty);

        var chapter = new Chapter
        {
            Title = "Chapter",
            Quests = { targetQuest, questWithMissingDependency },
        };

        var pack = new QuestPack();
        pack.Chapters.Add(chapter);

        var validator = new BrokenReferenceValidator();
        var issues = validator.Validate(pack).OrderBy(i => i.Path).ToList();

        Assert.Collection(
            issues,
            issue =>
            {
                Assert.Equal("BROKEN_DEPENDENCY_EMPTY", issue.Code);
                Assert.Equal("chapters[0].quests[1].dependencies[1]", issue.Path);
            },
            issue =>
            {
                Assert.Equal("BROKEN_DEPENDENCY_MISSING", issue.Code);
                Assert.Equal("chapters[0].quests[1].dependencies[0]", issue.Path);
            });
    }

    [Fact]
    public void Validate_ReturnsEmptyWhenAllDependenciesExist()
    {
        var questA = new Quest
        {
            Id = Guid.Parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            Title = "Quest A",
        };

        var questB = new Quest
        {
            Id = Guid.Parse("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
            Title = "Quest B",
        };

        questB.Dependencies.Add(questA.Id);

        var chapter = new Chapter
        {
            Title = "Chapter",
            Quests = { questA, questB },
        };

        var pack = new QuestPack();
        pack.Chapters.Add(chapter);

        var validator = new BrokenReferenceValidator();
        var issues = validator.Validate(pack);

        Assert.Empty(issues);
    }
}
