// <copyright file="XpRewardPropertiesViewModel.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Rewards;

public sealed class XpRewardPropertiesViewModel : RewardPropertiesViewModel
{
    private readonly XpReward reward;
    private int amount;
    private string? amountLocalError;
    private string? amountValidationError;
    private string? amountIssue;
    private bool levels;
    private string? levelsValidationError;
    private string? levelsIssue;

    public XpRewardPropertiesViewModel(XpReward reward, string pathPrefix)
        : base(reward, pathPrefix, "Experience Reward")
    {
        this.reward = reward;
        amount = reward.Amount;
        levels = reward.Levels;
        ValidateAmount(amount);
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

    public bool Levels
    {
        get => levels;
        set
        {
            if (SetProperty(ref levels, value))
            {
                reward.Levels = value;
                RefreshIssues();
            }
        }
    }

    public string? LevelsIssue
    {
        get => levelsIssue;
        private set => SetProperty(ref levelsIssue, value);
    }

    protected override void OnValidationIssuesChanged()
    {
        amountValidationError = GetIssueMessage("amount", "value");
        levelsValidationError = GetIssueMessage("levels");
        RefreshIssues();
    }

    private void ValidateAmount(int value)
    {
        if (value < 0)
        {
            amountLocalError = "Amount cannot be negative.";
        }
        else
        {
            reward.Amount = value;
            amountLocalError = null;
        }

        RefreshIssues();
    }

    private void RefreshIssues()
    {
        AmountIssue = CombineMessages(amountLocalError, amountValidationError);
        LevelsIssue = CombineMessages(null, levelsValidationError);
    }
}
