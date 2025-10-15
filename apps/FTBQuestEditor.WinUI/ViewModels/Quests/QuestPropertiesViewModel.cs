// <copyright file="QuestPropertiesViewModel.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.ObjectModel;
using FTBQuestEditor.WinUI.ViewModels;
using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuestEditor.WinUI.ViewModels.Rewards;
using FTBQuestEditor.WinUI.ViewModels.Tasks;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Quests;

public sealed class QuestPropertiesViewModel : ValidationAwareViewModel
{
    private readonly Quest quest;
    private string questId;
    private string? questIdLocalError;
    private string? questIdValidationError;
    private string? questIdIssue;
    private string title;
    private string? titleLocalError;
    private string? titleValidationError;
    private string? titleIssue;
    private string? subtitle;
    private string? subtitleValidationError;
    private string? subtitleIssue;
    private string iconId;
    private string? iconLocalError;
    private string? iconValidationError;
    private string? iconIssue;
    private int positionX;
    private string? positionXValidationError;
    private string? positionXIssue;
    private int positionY;
    private string? positionYValidationError;
    private string? positionYIssue;
    private int page;
    private string? pageLocalError;
    private string? pageValidationError;
    private string? pageIssue;

    public QuestPropertiesViewModel(Quest quest, string pathPrefix)
        : base(pathPrefix)
    {
        this.quest = quest;
        questId = quest.Id == Guid.Empty ? string.Empty : quest.Id.ToString();
        title = quest.Title;
        subtitle = quest.Subtitle;
        iconId = quest.IconId is { } icon ? IdentifierFormatting.ToDisplayString(icon) : string.Empty;
        positionX = quest.PositionX;
        positionY = quest.PositionY;
        page = quest.Page;
        Tasks = new ObservableCollection<TaskPropertiesViewModel>();
        Rewards = new ObservableCollection<RewardPropertiesViewModel>();

        InitializeTasks();
        InitializeRewards();

        ValidateQuestId(questId);
        ValidateTitle(title);
        ValidateIcon(iconId);
        ValidatePage(page);
    }

    public ObservableCollection<TaskPropertiesViewModel> Tasks { get; }

    public ObservableCollection<RewardPropertiesViewModel> Rewards { get; }

    public string QuestId
    {
        get => questId;
        set
        {
            if (SetProperty(ref questId, value))
            {
                ValidateQuestId(value);
            }
        }
    }

    public string? QuestIdIssue
    {
        get => questIdIssue;
        private set => SetProperty(ref questIdIssue, value);
    }

    public string Title
    {
        get => title;
        set
        {
            var sanitized = value ?? string.Empty;
            if (SetProperty(ref title, sanitized))
            {
                quest.Title = sanitized;
                titleLocalError = string.IsNullOrWhiteSpace(sanitized)
                    ? "Title is required."
                    : null;
                RefreshIssues();
            }
        }
    }

    public string? TitleIssue
    {
        get => titleIssue;
        private set => SetProperty(ref titleIssue, value);
    }

    public string? Subtitle
    {
        get => subtitle;
        set
        {
            if (SetProperty(ref subtitle, value))
            {
                quest.Subtitle = string.IsNullOrWhiteSpace(value) ? null : value;
                RefreshIssues();
            }
        }
    }

    public string? SubtitleIssue
    {
        get => subtitleIssue;
        private set => SetProperty(ref subtitleIssue, value);
    }

    public string IconId
    {
        get => iconId;
        set
        {
            if (SetProperty(ref iconId, value))
            {
                ValidateIcon(value);
            }
        }
    }

    public string? IconIssue
    {
        get => iconIssue;
        private set => SetProperty(ref iconIssue, value);
    }

    public int PositionX
    {
        get => positionX;
        set
        {
            if (SetProperty(ref positionX, value))
            {
                quest.PositionX = value;
                RefreshIssues();
            }
        }
    }

    public string? PositionXIssue
    {
        get => positionXIssue;
        private set => SetProperty(ref positionXIssue, value);
    }

    public int PositionY
    {
        get => positionY;
        set
        {
            if (SetProperty(ref positionY, value))
            {
                quest.PositionY = value;
                RefreshIssues();
            }
        }
    }

    public string? PositionYIssue
    {
        get => positionYIssue;
        private set => SetProperty(ref positionYIssue, value);
    }

    public int Page
    {
        get => page;
        set
        {
            if (SetProperty(ref page, value))
            {
                ValidatePage(value);
            }
        }
    }

    public string? PageIssue
    {
        get => pageIssue;
        private set => SetProperty(ref pageIssue, value);
    }

    protected override void OnValidationIssuesChanged()
    {
        questIdValidationError = GetIssueMessage("id");
        titleValidationError = GetIssueMessage("title");
        subtitleValidationError = GetIssueMessage("subtitle");
        iconValidationError = GetIssueMessage("icon", "iconId", "icon_id");
        positionXValidationError = GetIssueMessage("x");
        positionYValidationError = GetIssueMessage("y");
        pageValidationError = GetIssueMessage("page");
        RefreshIssues();

        foreach (var taskViewModel in Tasks)
        {
            taskViewModel.UpdateValidationIssues(CurrentIssues);
        }

        foreach (var rewardViewModel in Rewards)
        {
            rewardViewModel.UpdateValidationIssues(CurrentIssues);
        }
    }

    private void InitializeTasks()
    {
        Tasks.Clear();
        for (var index = 0; index < quest.Tasks.Count; index++)
        {
            var task = quest.Tasks[index];
            var viewModel = TaskViewModelFactory.Create(task, BuildPath($"tasks[{index}]"));
            viewModel.UpdateValidationIssues(CurrentIssues);
            Tasks.Add(viewModel);
        }
    }

    private void InitializeRewards()
    {
        Rewards.Clear();
        for (var index = 0; index < quest.Rewards.Count; index++)
        {
            var reward = quest.Rewards[index];
            var viewModel = RewardViewModelFactory.Create(reward, BuildPath($"rewards[{index}]"));
            viewModel.UpdateValidationIssues(CurrentIssues);
            Rewards.Add(viewModel);
        }
    }

    private void ValidateQuestId(string? value)
    {
        if (Guid.TryParse(value, out var parsed) && parsed != Guid.Empty)
        {
            quest.Id = parsed;
            questIdLocalError = null;
        }
        else
        {
            questIdLocalError = "Quest identifier must be a non-empty GUID.";
        }

        RefreshIssues();
    }

    private void ValidateTitle(string value)
    {
        quest.Title = value ?? string.Empty;
        titleLocalError = string.IsNullOrWhiteSpace(value) ? "Title is required." : null;
        RefreshIssues();
    }

    private void ValidateIcon(string? value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            quest.IconId = null;
            iconLocalError = null;
        }
        else if (IdentifierFormatting.TryParse(value, out var identifier))
        {
            quest.IconId = identifier;
            iconLocalError = null;
        }
        else
        {
            iconLocalError = "Icon identifier must be provided in namespace:path format.";
        }

        RefreshIssues();
    }

    private void ValidatePage(int value)
    {
        if (value < 0)
        {
            pageLocalError = "Page cannot be negative.";
        }
        else
        {
            quest.Page = value;
            pageLocalError = null;
        }

        RefreshIssues();
    }

    private void RefreshIssues()
    {
        QuestIdIssue = CombineMessages(questIdLocalError, questIdValidationError);
        TitleIssue = CombineMessages(titleLocalError, titleValidationError);
        SubtitleIssue = CombineMessages(null, subtitleValidationError);
        IconIssue = CombineMessages(iconLocalError, iconValidationError);
        PositionXIssue = CombineMessages(null, positionXValidationError);
        PositionYIssue = CombineMessages(null, positionYValidationError);
        PageIssue = CombineMessages(pageLocalError, pageValidationError);
    }
}
