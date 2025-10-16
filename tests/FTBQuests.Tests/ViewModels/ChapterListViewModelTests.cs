using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="ChapterListViewModelTests.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.Generic;
using System.Linq;
using FTBQuestEditor.WinUI.ViewModels;
using FTBQuests.Codecs.Model;
using FTBQuests.IO;
using FTBQuests.Validation;
using Newtonsoft.Json.Linq;
using Xunit;

namespace FTBQuests.Tests.ViewModels;

public class ChapterListViewModelTests
{
    [Fact]
    public void BuildsTreeWithGroupsAndValidation()
    {
        var pack = CreatePack(out var chapterOne, out var chapterTwo, out var chapterThree);
        pack.Metadata.Add("chapter_groups.json", JToken.Parse("""
            [
              {
                "id": "group-a",
                "title": "Getting Started",
                "chapters": [1, 2]
              }
            ]
            """));

        var issues = new[]
        {
            new ValidationIssue(ValidationSeverity.Error, "chapters[0].id", "Missing id", "missing-id"),
            new ValidationIssue(ValidationSeverity.Warning, "chapters[1].title", "Missing title", "missing-title"),
        };

        var viewModel = new ChapterListViewModel(new[] { new StubValidator(issues) });
        viewModel.LoadQuestPack(pack);

        Assert.Equal(chapterOne, viewModel.SelectedChapter);

        Assert.Equal(2, viewModel.Nodes.Count);
        var groupNode = viewModel.Nodes[0];
        Assert.True(groupNode.IsGroup);
        Assert.Equal(ChapterValidationState.Error, groupNode.ValidationState);
        Assert.Equal(2, groupNode.Children.Count);
        Assert.Equal(chapterOne, groupNode.Children[0].Chapter);
        Assert.Equal(ChapterValidationState.Error, groupNode.Children[0].ValidationState);
        Assert.Equal(chapterTwo, groupNode.Children[1].Chapter);
        Assert.Equal(ChapterValidationState.Warning, groupNode.Children[1].ValidationState);

        var leftoverNode = viewModel.Nodes[1];
        Assert.False(leftoverNode.IsGroup);
        Assert.Equal(chapterThree, leftoverNode.Chapter);
        Assert.Equal(ChapterValidationState.None, leftoverNode.ValidationState);
    }

    [Fact]
    public void FilterRestrictsVisibleChapters()
    {
        var pack = CreatePack(out _, out _, out _);
        pack.Metadata.Add("chapter_groups.json", JToken.Parse("""
            [
              {
                "id": "group-a",
                "title": "Getting Started",
                "chapters": [1, 2]
              }
            ]
            """));

        var viewModel = new ChapterListViewModel(Array.Empty<IValidator>());
        viewModel.LoadQuestPack(pack);

        viewModel.FilterText = "adv";

        Assert.Single(viewModel.Nodes);
        var group = viewModel.Nodes[0];
        Assert.True(group.IsGroup);
        Assert.Single(group.Children);
        Assert.Equal("Advanced", group.Children[0].Chapter?.Title);
    }

    [Fact]
    public void SelectChapterCommandUpdatesSelection()
    {
        var pack = CreatePack(out _, out var chapterTwo, out _);
        pack.Metadata.Add("chapter_groups.json", JToken.Parse("""
            [
              {
                "id": "group-a",
                "title": "Getting Started",
                "chapters": [1, 2]
              }
            ]
            """));

        var viewModel = new ChapterListViewModel(Array.Empty<IValidator>());
        Chapter? observed = null;
        viewModel.ChapterSelected += (_, chapter) => observed = chapter;

        viewModel.LoadQuestPack(pack);

        var groupNode = viewModel.Nodes[0];
        var targetNode = groupNode.Children.First(child => Equals(child.Chapter, chapterTwo));
        viewModel.SelectChapterCommand.Execute(targetNode);

        Assert.Equal(chapterTwo, viewModel.SelectedChapter);
        Assert.Equal(chapterTwo, observed);

        viewModel.SelectChapterCommand.Execute(null);
        Assert.Null(viewModel.SelectedChapter);
    }

    private static FTBQuests.IO.QuestPack CreatePack(out Chapter chapterOne, out Chapter chapterTwo, out Chapter chapterThree)
    {
        var pack = new FTBQuests.IO.QuestPack();

        chapterOne = new Chapter
        {
            Id = 1,
            Title = "Basics",
        };

        chapterTwo = new Chapter
        {
            Id = 2,
            Title = "Advanced",
        };

        chapterThree = new Chapter
        {
            Id = 3,
            Title = "Extras",
        };

        pack.AddChapter(chapterOne);
        pack.AddChapter(chapterTwo);
        pack.AddChapter(chapterThree);

        return pack;
    }

    private sealed class StubValidator : IValidator
    {
        private readonly IReadOnlyList<ValidationIssue> _issues;

        public StubValidator(IReadOnlyList<ValidationIssue> issues)
        {
            _issues = issues;
        }

        public IEnumerable<ValidationIssue> Validate(FTBQuests.IO.QuestPack FTBQuests.IO.QuestPack)
        {
            return _issues;
        }
    }
}

