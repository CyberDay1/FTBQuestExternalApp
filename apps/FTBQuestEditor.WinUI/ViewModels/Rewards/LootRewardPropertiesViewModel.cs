// <copyright file="LootRewardPropertiesViewModel.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using FTBQuestEditor.WinUI.ViewModels;
using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Rewards;

public sealed class LootRewardPropertiesViewModel : RewardPropertiesViewModel
{
    private readonly LootReward reward;
    private string lootTable;
    private string? lootLocalError;
    private string? lootValidationError;
    private string? lootIssue;

    public LootRewardPropertiesViewModel(LootReward reward, string pathPrefix)
        : base(reward, pathPrefix, "Loot Reward")
    {
        this.reward = reward;
        lootTable = IdentifierFormatting.ToDisplayString(reward.LootTable);
        ValidateLootTable(lootTable);
    }

    public string LootTable
    {
        get => lootTable;
        set
        {
            if (SetProperty(ref lootTable, value))
            {
                ValidateLootTable(value);
            }
        }
    }

    public string? LootIssue
    {
        get => lootIssue;
        private set => SetProperty(ref lootIssue, value);
    }

    protected override void OnValidationIssuesChanged()
    {
        lootValidationError = GetIssueMessage("table", "loot_table");
        RefreshIssues();
    }

    private void ValidateLootTable(string? value)
    {
        if (IdentifierFormatting.TryParse(value, out var identifier))
        {
            reward.LootTable = identifier;
            lootLocalError = null;
        }
        else
        {
            lootLocalError = "Loot table identifier must be provided in namespace:path format.";
        }

        RefreshIssues();
    }

    private void RefreshIssues()
    {
        LootIssue = CombineMessages(lootLocalError, lootValidationError);
    }
}
