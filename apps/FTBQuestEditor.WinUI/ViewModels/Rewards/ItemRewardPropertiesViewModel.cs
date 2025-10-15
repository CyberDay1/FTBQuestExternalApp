// <copyright file="ItemRewardPropertiesViewModel.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using FTBQuestEditor.WinUI.ViewModels;
using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Rewards;

public sealed class ItemRewardPropertiesViewModel : RewardPropertiesViewModel
{
    private readonly ItemReward reward;
    private string itemId;
    private string? itemLocalError;
    private string? itemValidationError;
    private string? itemIssue;
    private int count;
    private string? countLocalError;
    private string? countValidationError;
    private string? countIssue;
    private string? nbt;
    private string? nbtValidationError;
    private string? nbtIssue;

    public ItemRewardPropertiesViewModel(ItemReward reward, string pathPrefix)
        : base(reward, pathPrefix, "Item Reward")
    {
        this.reward = reward;
        itemId = IdentifierFormatting.ToDisplayString(reward.ItemId);
        count = reward.Count <= 0 ? 1 : reward.Count;
        nbt = reward.Nbt;
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

    public string? ItemIssue
    {
        get => itemIssue;
        private set => SetProperty(ref itemIssue, value);
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
                reward.Nbt = string.IsNullOrWhiteSpace(value) ? null : value;
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
        itemValidationError = GetIssueMessage("item");
        countValidationError = GetIssueMessage("count");
        nbtValidationError = GetIssueMessage("nbt");
        RefreshIssues();
    }

    private void ValidateItemId(string? value)
    {
        if (IdentifierFormatting.TryParse(value, out var identifier))
        {
            reward.ItemId = identifier;
            itemLocalError = null;
        }
        else
        {
            itemLocalError = "Item identifier must be provided in namespace:path format.";
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
            reward.Count = value;
            countLocalError = null;
        }

        RefreshIssues();
    }

    private void RefreshIssues()
    {
        ItemIssue = CombineMessages(itemLocalError, itemValidationError);
        CountIssue = CombineMessages(countLocalError, countValidationError);
        NbtIssue = CombineMessages(null, nbtValidationError);
    }
}
