// <copyright file="NbtTaskPropertiesViewModel.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using FTBQuestEditor.WinUI.ViewModels;
using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Tasks;

public sealed class NbtTaskPropertiesViewModel : TaskPropertiesViewModel
{
    private readonly NbtTask task;
    private string targetId;
    private string? targetLocalError;
    private string? targetValidationError;
    private string? targetIssue;
    private string? requiredNbt;
    private string? requiredNbtValidationError;
    private string? requiredNbtIssue;

    public NbtTaskPropertiesViewModel(NbtTask task, string pathPrefix)
        : base(task, pathPrefix, "NBT Task")
    {
        this.task = task;
        targetId = IdentifierFormatting.ToDisplayString(task.TargetId);
        requiredNbt = task.RequiredNbt;
        ValidateTargetId(targetId);
    }

    public string TargetId
    {
        get => targetId;
        set
        {
            if (SetProperty(ref targetId, value))
            {
                ValidateTargetId(value);
            }
        }
    }

    public string? TargetIssue
    {
        get => targetIssue;
        private set => SetProperty(ref targetIssue, value);
    }

    public string? RequiredNbt
    {
        get => requiredNbt;
        set
        {
            if (SetProperty(ref requiredNbt, value))
            {
                task.RequiredNbt = string.IsNullOrWhiteSpace(value) ? null : value;
                RefreshIssues();
            }
        }
    }

    public string? RequiredNbtIssue
    {
        get => requiredNbtIssue;
        private set => SetProperty(ref requiredNbtIssue, value);
    }

    protected override void OnValidationIssuesChanged()
    {
        targetValidationError = GetIssueMessage("target", "target_id");
        requiredNbtValidationError = GetIssueMessage("nbt", "required_nbt");
        RefreshIssues();
    }

    private void ValidateTargetId(string? value)
    {
        if (IdentifierFormatting.TryParse(value, out var identifier))
        {
            task.TargetId = identifier;
            targetLocalError = null;
        }
        else
        {
            targetLocalError = "Target identifier must be provided in namespace:path format.";
        }

        RefreshIssues();
    }

    private void RefreshIssues()
    {
        TargetIssue = CombineMessages(targetLocalError, targetValidationError);
        RequiredNbtIssue = CombineMessages(null, requiredNbtValidationError);
    }
}
