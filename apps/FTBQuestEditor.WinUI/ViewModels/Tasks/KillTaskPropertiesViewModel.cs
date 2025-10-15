// <copyright file="KillTaskPropertiesViewModel.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using FTBQuestEditor.WinUI.ViewModels;
using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Tasks;

public sealed class KillTaskPropertiesViewModel : TaskPropertiesViewModel
{
    private readonly KillTask task;
    private string entityId;
    private string? entityLocalError;
    private string? entityValidationError;
    private string? entityIssue;
    private int amount;
    private string? amountLocalError;
    private string? amountValidationError;
    private string? amountIssue;

    public KillTaskPropertiesViewModel(KillTask task, string pathPrefix)
        : base(task, pathPrefix, "Kill Task")
    {
        this.task = task;
        entityId = IdentifierFormatting.ToDisplayString(task.EntityId);
        amount = task.Amount <= 0 ? 1 : task.Amount;
        ValidateEntityId(entityId);
        ValidateAmount(amount);
    }

    public string EntityId
    {
        get => entityId;
        set
        {
            if (SetProperty(ref entityId, value))
            {
                ValidateEntityId(value);
            }
        }
    }

    public string? EntityIssue
    {
        get => entityIssue;
        private set => SetProperty(ref entityIssue, value);
    }

    public int Amount
    {
        get => amount;
        set
        {
            if (SetProperty(ref amount, value))
            {
                ValidateAmount(value);
            }
        }
    }

    public string? AmountIssue
    {
        get => amountIssue;
        private set => SetProperty(ref amountIssue, value);
    }

    protected override void OnValidationIssuesChanged()
    {
        entityValidationError = GetIssueMessage("entity", "entity_id");
        amountValidationError = GetIssueMessage("amount", "value");
        RefreshIssues();
    }

    private void ValidateEntityId(string? value)
    {
        if (IdentifierFormatting.TryParse(value, out var identifier))
        {
            task.EntityId = identifier;
            entityLocalError = null;
        }
        else
        {
            entityLocalError = "Entity identifier must be provided in namespace:path format.";
        }

        RefreshIssues();
    }

    private void ValidateAmount(int value)
    {
        if (value <= 0)
        {
            amountLocalError = "Amount must be greater than zero.";
        }
        else
        {
            task.Amount = value;
            amountLocalError = null;
        }

        RefreshIssues();
    }

    private void RefreshIssues()
    {
        EntityIssue = CombineMessages(entityLocalError, entityValidationError);
        AmountIssue = CombineMessages(amountLocalError, amountValidationError);
    }
}
