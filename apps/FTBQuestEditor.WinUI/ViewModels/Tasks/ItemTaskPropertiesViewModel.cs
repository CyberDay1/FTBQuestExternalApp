using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="ItemTaskPropertiesViewModel.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using FTBQuestEditor.WinUI.ViewModels;
using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Tasks;

public sealed class ItemTaskPropertiesViewModel : TaskPropertiesViewModel
{
    private readonly ItemTask task;
    private string itemId;
    private string? itemIdLocalError;
    private string? itemIdValidationError;
    private string? itemIdIssue;
    private int count;
    private string? countLocalError;
    private string? countValidationError;
    private string? countIssue;
    private string? nbt;
    private string? nbtValidationError;
    private string? nbtIssue;

    public ItemTaskPropertiesViewModel(ItemTask task, string pathPrefix)
        : base(task, pathPrefix, "Item Task")
    {
        this.task = task;
        itemId = IdentifierFormatting.ToDisplayString(task.ItemId);
        count = task.Count == 0 ? 1 : task.Count;
        nbt = task.Nbt;
        ValidateItemId(itemId);
        ValidateCount(count);
    }

    public string ItemId
    {
        get => itemId;
        set
        {
            if (SetProperty(ref itemId, value))
            {
                ValidateItemId(value);
            }
        }
    }

    public string? ItemIdIssue
    {
        get => itemIdIssue;
        private set => SetProperty(ref itemIdIssue, value);
    }

    public int Count
    {
        get => count;
        set
        {
            if (SetProperty(ref count, value))
            {
                ValidateCount(value);
            }
        }
    }

    public string? CountIssue
    {
        get => countIssue;
        private set => SetProperty(ref countIssue, value);
    }

    public string? Nbt
    {
        get => nbt;
        set
        {
            if (SetProperty(ref nbt, value))
            {
                task.Nbt = string.IsNullOrWhiteSpace(value) ? null : value;
                RefreshIssues();
            }
        }
    }

    public string? NbtIssue
    {
        get => nbtIssue;
        private set => SetProperty(ref nbtIssue, value);
    }

    protected override void OnValidationIssuesChanged()
    {
        itemIdValidationError = GetIssueMessage("item");
        countValidationError = GetIssueMessage("count");
        nbtValidationError = GetIssueMessage("nbt");
        RefreshIssues();
    }

    private void ValidateItemId(string? value)
    {
        if (IdentifierFormatting.TryParse(value, out var identifier))
        {
            task.ItemId = identifier;
            itemIdLocalError = null;
        }
        else
        {
            itemIdLocalError = "Item identifier must be provided in namespace:path format.";
        }

        RefreshIssues();
    }

    private void ValidateCount(int value)
    {
        if (value <= 0)
        {
            countLocalError = "Count must be greater than zero.";
        }
        else
        {
            task.Count = value;
            countLocalError = null;
        }

        RefreshIssues();
    }

    private void RefreshIssues()
    {
        ItemIdIssue = CombineMessages(itemIdLocalError, itemIdValidationError);
        CountIssue = CombineMessages(countLocalError, countValidationError);
        NbtIssue = CombineMessages(null, nbtValidationError);
    }
}
