using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="CommandRewardPropertiesViewModel.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuests.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Rewards;

public sealed class CommandRewardPropertiesViewModel : RewardPropertiesViewModel
{
    private readonly CommandReward reward;
    private string command;
    private string? commandLocalError;
    private string? commandValidationError;
    private string? commandIssue;

    public CommandRewardPropertiesViewModel(CommandReward reward, string pathPrefix)
        : base(reward, pathPrefix, "Command Reward")
    {
        this.reward = reward;
        command = reward.Command;
        ValidateCommand(command);
    }

    public string Command
    {
        get => command;
        set
        {
            if (SetProperty(ref command, value))
            {
                ValidateCommand(value);
            }
        }
    }

    public string? CommandIssue
    {
        get => commandIssue;
        private set => SetProperty(ref commandIssue, value);
    }

    protected override void OnValidationIssuesChanged()
    {
        commandValidationError = GetIssueMessage("command");
        RefreshIssues();
    }

    private void ValidateCommand(string? value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            commandLocalError = "Command text is required.";
        }
        else
        {
            reward.Command = value;
            commandLocalError = null;
        }

        RefreshIssues();
    }

    private void RefreshIssues()
    {
        CommandIssue = CombineMessages(commandLocalError, commandValidationError);
    }
}

