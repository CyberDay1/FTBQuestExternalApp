using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="UnknownRewardPropertiesViewModel.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuests.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Rewards;

public sealed class UnknownRewardPropertiesViewModel : RewardPropertiesViewModel
{
    public UnknownRewardPropertiesViewModel(UnknownReward reward, string pathPrefix)
        : base(reward, pathPrefix, $"Unknown Reward ({reward.TypeId})")
    {
    }

    public string Description => "This reward type is not recognized. Editing is limited to raw JSON.";

    protected override void OnValidationIssuesChanged()
    {
    }
}

