// <copyright file="CycleValidatorTests.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System.Collections.Generic;
using System.Linq;
using FTBQuestExternalApp.Codecs.Model;
using FTBQuests.Validation.Validators;
using Xunit;

namespace FTBQuests.Tests;

public class CycleValidatorTests
{
    [Fact]
    public void Validate_ReturnsEmpty_WhenNoCycles()
    {
        var chapter = new Chapter
        {
            Id = 10,
            Title = "Chapter 1",
            Quests = new List<Quest>
            {
                new()
                {
                    Id = 11,
                    Title = "Quest A",
                },
                new()
                {
                    Id = 12,
                    Title = "Quest B",
                },
            },
        };

        var validator = new CycleValidator();

        var result = validator.Validate(new[] { chapter });

        Assert.Empty(result);
    }

    [Fact]
    public void Validate_DetectsCycleAcrossChapters()
    {
        var questA = new Quest { Id = 21, Title = "Quest A" };
        var questB = new Quest { Id = 22, Title = "Quest B" };
        var questC = new Quest { Id = 23, Title = "Quest C" };

        questA.Dependencies.Add(questB.Id);
        questB.Dependencies.Add(questC.Id);
        questC.Dependencies.Add(questA.Id);

        var chapterOne = new Chapter
        {
            Id = 24,
            Title = "Chapter One",
            Quests = new List<Quest> { questA },
        };

        var chapterTwo = new Chapter
        {
            Id = 25,
            Title = "Chapter Two",
            Quests = new List<Quest> { questB, questC },
        };

        var validator = new CycleValidator();

        var result = validator.Validate(new[] { chapterOne, chapterTwo });

        var cycle = Assert.Single(result);
        Assert.Collection(
            cycle.Path,
            node => Assert.Equal(questA.Id, node.QuestId),
            node => Assert.Equal(questB.Id, node.QuestId),
            node => Assert.Equal(questC.Id, node.QuestId),
            node => Assert.Equal(questA.Id, node.QuestId));

        Assert.Equal(new[] { "Chapter One", "Chapter Two", "Chapter Two", "Chapter One" }, cycle.Path.Select(n => n.ChapterTitle));
        Assert.Equal(new[] { "Quest A", "Quest B", "Quest C", "Quest A" }, cycle.Path.Select(n => n.QuestTitle));
    }

    [Fact]
    public void Validate_DetectsMultipleDistinctCyclesDeterministically()
    {
        var questA = new Quest { Id = 31, Title = "Quest A" };
        var questB = new Quest { Id = 32, Title = "Quest B" };
        var questC = new Quest { Id = 33, Title = "Quest C" };
        var questD = new Quest { Id = 34, Title = "Quest D" };

        questA.Dependencies.Add(questB.Id);
        questB.Dependencies.Add(questA.Id);

        questC.Dependencies.Add(questD.Id);
        questD.Dependencies.Add(questC.Id);

        var chapterOne = new Chapter
        {
            Id = 35,
            Title = "Chapter One",
            Quests = new List<Quest> { questA, questB },
        };

        var chapterTwo = new Chapter
        {
            Id = 36,
            Title = "Chapter Two",
            Quests = new List<Quest> { questC, questD },
        };

        var validator = new CycleValidator();

        var result = validator.Validate(new[] { chapterOne, chapterTwo });

        Assert.Equal(2, result.Count);
        Assert.All(result, r => Assert.Equal(r.Path.First().QuestId, r.Path.Last().QuestId));

        Assert.Equal(
            new[]
            {
                new[] { questA.Id, questB.Id, questA.Id },
                new[] { questC.Id, questD.Id, questC.Id },
            },
            result.Select(r => r.Path.Select(n => n.QuestId).ToArray()));
    }
}
