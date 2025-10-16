using FTBQuests.Validation;
using FTBQuests.Assets;
using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.Linq;
using System.Windows.Input;
using FTBQuests.Codecs.Model;
using FTBQuests.Validation;
using FTBQuests.Validation.Validators;

namespace FTBQuestEditor.WinUI.ViewModels;

/// <summary>
/// Coordinates quest nodes, dependency links, and validation state for the graph overlay.
/// </summary>
public sealed class QuestGraphViewModel : ObservableObject
{
    private readonly ObservableCollection<QuestNodeViewModel> quests = new();
    private readonly ObservableCollection<Link> links = new();
    private readonly ObservableCollection<ValidationIssue> validationIssues = new();
    private readonly Dictionary<Guid, QuestNodeViewModel> questLookup = new();
    private readonly List<Chapter> chapters = new();

    public QuestGraphViewModel(IEnumerable<Chapter> chapters)
    {
        if (chapters is null)
        {
            throw new ArgumentNullException(nameof(chapters));
        }

        CreateLinkCommand = new RelayCommand(OnCreateLink);
        DeleteLinkCommand = new RelayCommand(OnDeleteLink);

        LoadChapters(chapters);
    }

    public ObservableCollection<QuestNodeViewModel> Quests => quests;

    public ObservableCollection<Link> Links => links;

    public ObservableCollection<ValidationIssue> ValidationIssues => validationIssues;

    public ICommand CreateLinkCommand { get; }

    public ICommand DeleteLinkCommand { get; }

    public IReadOnlyList<Chapter> Chapters => chapters;

    public static QuestGraphViewModel CreateSample()
    {
        var chapter = new Chapter
        {
            Title = "Getting Started",
            Quests =
            [
                new Quest
                {
                    Id = Guid.NewGuid(),
                    Title = "Gather Wood",
                    PositionX = 0,
                    PositionY = 0,
                },
                new Quest
                {
                    Id = Guid.NewGuid(),
                    Title = "Craft Planks",
                    PositionX = 1,
                    PositionY = 0,
                },
                new Quest
                {
                    Id = Guid.NewGuid(),
                    Title = "Build a Crafting Table",
                    PositionX = 2,
                    PositionY = 0,
                },
                new Quest
                {
                    Id = Guid.NewGuid(),
                    Title = "Craft Tools",
                    PositionX = 3,
                    PositionY = 0,
                },
            ],
        };

        chapter.Quests[1].Dependencies.Add(chapter.Quests[0].Id);
        chapter.Quests[2].Dependencies.Add(chapter.Quests[1].Id);

        return new QuestGraphViewModel(new[] { chapter });
    }

    public void LoadChapters(IEnumerable<Chapter> newChapters)
    {
        quests.CollectionChanged -= OnQuestsCollectionChanged;
        quests.Clear();
        links.Clear();
        validationIssues.Clear();
        questLookup.Clear();
        chapters.Clear();

        foreach (var chapter in newChapters)
        {
            if (chapter is null)
            {
                continue;
            }

            chapters.Add(chapter);

            if (chapter.Quests is null)
            {
                continue;
            }

            foreach (var quest in chapter.Quests)
            {
                if (quest is null)
                {
                    continue;
                }

                var node = new QuestNodeViewModel(quest);
                quests.Add(node);
                questLookup[node.Id] = node;
            }
        }

        foreach (var node in quests)
        {
            foreach (var dependencyId in node.Dependencies)
            {
                if (!questLookup.ContainsKey(dependencyId))
                {
                    continue;
                }

                var link = new Link(dependencyId, node.Id);
                if (!links.Contains(link))
                {
                    links.Add(link);
                }
            }
        }

        quests.CollectionChanged += OnQuestsCollectionChanged;
        RefreshValidation();
    }

    private void OnCreateLink(object? parameter)
    {
        if (parameter is not Link link)
        {
            return;
        }

        if (!questLookup.TryGetValue(link.SourceId, out var source) || !questLookup.TryGetValue(link.TargetId, out var target))
        {
            return;
        }

        if (!target.AddDependency(source.Id))
        {
            return;
        }

        if (!links.Contains(link))
        {
            links.Add(link);
        }

        RefreshValidation();
    }

    private void OnDeleteLink(object? parameter)
    {
        if (parameter is not Link link)
        {
            return;
        }

        if (!questLookup.TryGetValue(link.TargetId, out var target))
        {
            return;
        }

        if (!target.RemoveDependency(link.SourceId))
        {
            return;
        }

        links.Remove(link);
        RefreshValidation();
    }

    private void RefreshValidation()
    {
        validationIssues.Clear();

        var cycleValidator = new CycleValidator();
        var results = cycleValidator.Validate(chapters);

        foreach (var result in results)
        {
            if (result.Path.Count == 0)
            {
                continue;
            }

            var questTitles = result.Path
                .Select(node => string.IsNullOrWhiteSpace(node.QuestTitle) ? node.QuestId.ToString("D") : node.QuestTitle)
                .ToList();

            var message = $"Dependency cycle detected: {string.Join(" → ", questTitles)}";
            var issue = new ValidationIssue(ValidationSeverity.Error, "quests", message, "dependency_cycle");
            validationIssues.Add(issue);
        }
    }

    private void OnQuestsCollectionChanged(object? sender, NotifyCollectionChangedEventArgs e)
    {
        RefreshValidation();
    }
}

