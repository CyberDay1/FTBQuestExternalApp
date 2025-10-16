using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="MainViewModel.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.ComponentModel;
using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuestEditor.WinUI.ViewModels.Quests;
using FTBQuestEditor.WinUI.ViewModels.Rewards;
using FTBQuestEditor.WinUI.ViewModels.Tasks;
using FTBQuests.Codecs.Model;
using FTBQuests.Validation;
using FTBQuests.Validation.Validators;

namespace FTBQuestEditor.WinUI.ViewModels;

public sealed class MainViewModel : ObservableObject
{
    private readonly FTBQuests.IO.QuestPack FTBQuests.IO.QuestPack;
    private readonly Chapter chapter;
    private readonly Quest quest;
    private readonly List<IValidator> validators;
    private readonly ObservableCollection<ValidationIssue> validationIssues;
    private readonly ReadOnlyObservableCollection<ValidationIssue> readonlyValidationIssues;

    public MainViewModel()
    {
        FTBQuests.IO.QuestPack = new FTBQuests.IO.QuestPack();
        chapter = new Chapter
        {
            Id = Guid.NewGuid(),
            Title = "Chapter",
        };

        quest = new Quest
        {
            Id = Guid.NewGuid(),
            Title = string.Empty,
            Subtitle = null,
            PositionX = 0,
            PositionY = 0,
            Page = 0,
        };

        quest.AddTask(new ItemTask
        {
            ItemId = "minecraft:stone",
            Count = 16,
        });
        quest.AddReward(new XpReward
        {
            Amount = 50,
            Levels = false,
        });

        chapter.AddQuest(quest);
        FTBQuests.IO.QuestPack.AddChapter(chapter);

        Quest = new QuestPropertiesViewModel(quest, "chapters[0].quests[0]");

        validators = new List<IValidator>
        {
            new RequiredFieldsValidator(),
            new BrokenReferenceValidator(),
        };

        validationIssues = new ObservableCollection<ValidationIssue>();
        readonlyValidationIssues = new ReadOnlyObservableCollection<ValidationIssue>(validationIssues);

        AttachHandlers();
        RecalculateValidation();
    }

    public QuestPropertiesViewModel Quest { get; }

    public ReadOnlyObservableCollection<ValidationIssue> ValidationIssues => readonlyValidationIssues;

    private void AttachHandlers()
    {
        Quest.PropertyChanged += OnQuestPropertyChanged;
        Quest.Tasks.CollectionChanged += OnTasksCollectionChanged;
        foreach (var task in Quest.Tasks)
        {
            task.PropertyChanged += OnChildPropertyChanged;
        }

        Quest.Rewards.CollectionChanged += OnRewardsCollectionChanged;
        foreach (var reward in Quest.Rewards)
        {
            reward.PropertyChanged += OnChildPropertyChanged;
        }
    }

    private void OnTasksCollectionChanged(object? sender, NotifyCollectionChangedEventArgs e)
    {
        if (e.NewItems is not null)
        {
            foreach (TaskPropertiesViewModel task in e.NewItems)
            {
                task.PropertyChanged += OnChildPropertyChanged;
            }
        }

        if (e.OldItems is not null)
        {
            foreach (TaskPropertiesViewModel task in e.OldItems)
            {
                task.PropertyChanged -= OnChildPropertyChanged;
            }
        }

        RecalculateValidation();
    }

    private void OnRewardsCollectionChanged(object? sender, NotifyCollectionChangedEventArgs e)
    {
        if (e.NewItems is not null)
        {
            foreach (RewardPropertiesViewModel reward in e.NewItems)
            {
                reward.PropertyChanged += OnChildPropertyChanged;
            }
        }

        if (e.OldItems is not null)
        {
            foreach (RewardPropertiesViewModel reward in e.OldItems)
            {
                reward.PropertyChanged -= OnChildPropertyChanged;
            }
        }

        RecalculateValidation();
    }

    private void OnQuestPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (ShouldIgnoreProperty(e.PropertyName))
        {
            return;
        }

        RecalculateValidation();
    }

    private void OnChildPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (ShouldIgnoreProperty(e.PropertyName))
        {
            return;
        }

        RecalculateValidation();
    }

    private static bool ShouldIgnoreProperty(string? propertyName)
    {
        if (string.IsNullOrEmpty(propertyName))
        {
            return false;
        }

        return propertyName.EndsWith("Issue", StringComparison.Ordinal);
    }

    private void RecalculateValidation()
    {
        validationIssues.Clear();

        foreach (var validator in validators)
        {
            foreach (var issue in validator.Validate(FTBQuests.IO.QuestPack))
            {
                validationIssues.Add(issue);
            }
        }

        Quest.UpdateValidationIssues(validationIssues);
    }
}

