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
            Id = 101,
            Title = "Existing Quest",
        };

        var questWithMissingDependency = new Quest
        {
            Id = 202,
            Title = "Needs Friend",
        };

        questWithMissingDependency.AddDependency(303);
        questWithMissingDependency.AddDependency(0);

        var chapter = new Chapter
        {
            Title = "Chapter",
        };
        chapter.AddQuest(targetQuest);
        chapter.AddQuest(questWithMissingDependency);

        var pack = new QuestPack();
        pack.AddChapter(chapter);

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
            Id = 401,
            Title = "Quest A",
        };

        var questB = new Quest
        {
            Id = 402,
            Title = "Quest B",
        };

        questB.AddDependency(questA.Id);

        var chapter = new Chapter
        {
            Title = "Chapter",
        };
        chapter.AddQuest(questA);
        chapter.AddQuest(questB);

        var pack = new QuestPack();
        pack.AddChapter(chapter);

        var validator = new BrokenReferenceValidator();
        var issues = validator.Validate(pack);

        Assert.Empty(issues);
    }
}
