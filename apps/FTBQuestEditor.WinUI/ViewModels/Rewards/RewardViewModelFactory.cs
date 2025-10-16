using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="RewardViewModelFactory.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using FTBQuestEditor.WinUI.ViewModels.Rewards;
using FTBQuests.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Rewards;

internal static class RewardViewModelFactory
{
    public static RewardPropertiesViewModel Create(IReward reward, string pathPrefix)
    {
        return reward switch
        {
            ItemReward itemReward => new ItemRewardPropertiesViewModel(itemReward, pathPrefix),
            LootReward lootReward => new LootRewardPropertiesViewModel(lootReward, pathPrefix),
            XpReward xpReward => new XpRewardPropertiesViewModel(xpReward, pathPrefix),
            CommandReward commandReward => new CommandRewardPropertiesViewModel(commandReward, pathPrefix),
            CustomReward customReward => new CustomRewardPropertiesViewModel(customReward, pathPrefix),
            UnknownReward unknownReward => new UnknownRewardPropertiesViewModel(unknownReward, pathPrefix),
            _ => new ExternalRewardPropertiesViewModel(reward, pathPrefix),
        };
    }
}

