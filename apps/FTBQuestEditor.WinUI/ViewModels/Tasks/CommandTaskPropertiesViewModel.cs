using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="CommandTaskPropertiesViewModel.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuests.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Tasks;

public sealed class CommandTaskPropertiesViewModel : TaskPropertiesViewModel
{
    private readonly CommandTask task;
    private string? command;
    private string? commandLocalError;
    private string? commandValidationError;
    private string? commandIssue;

    public CommandTaskPropertiesViewModel(CommandTask task, string pathPrefix)
        : base(task, pathPrefix, "Command Task")
    {
        this.task = task;
        command = task.Command;
        ValidateCommand(command);
    }

    public string? Command
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
            task.Command = value;
            commandLocalError = null;
        }

        RefreshIssues();
    }

    private void RefreshIssues()
    {
        CommandIssue = CombineMessages(commandLocalError, commandValidationError);
    }
}

