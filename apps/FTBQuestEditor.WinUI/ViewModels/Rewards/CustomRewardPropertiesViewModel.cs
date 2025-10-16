using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="CustomRewardPropertiesViewModel.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Rewards;

public sealed class CustomRewardPropertiesViewModel : RewardPropertiesViewModel
{
    public CustomRewardPropertiesViewModel(CustomReward reward, string pathPrefix)
        : base(reward, pathPrefix, "Custom Reward")
    {
    }

    public string Description => "Custom rewards expose additional JSON-defined fields.";

    protected override void OnValidationIssuesChanged()
    {
    }
}
