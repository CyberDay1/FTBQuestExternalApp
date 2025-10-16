using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="RewardPropertiesViewModel.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Rewards;

public abstract class RewardPropertiesViewModel : ValidationAwareViewModel
{
    protected RewardPropertiesViewModel(IReward reward, string pathPrefix, string displayName)
        : base(pathPrefix)
    {
        Reward = reward;
        DisplayName = displayName;
    }

    public IReward Reward { get; }

    public string DisplayName { get; }

    public string TypeId => Reward.TypeId;
}
