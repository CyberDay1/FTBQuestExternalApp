using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="RewardDiscriminator.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.Generic;
using FTBQuestExternalApp.Codecs.Enums;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestExternalApp.Codecs.Serialization;

public static class RewardDiscriminator
{
    private static readonly IReadOnlyDictionary<RewardType, Type> RewardTypeMappings =
        new Dictionary<RewardType, Type>
        {
            [RewardType.Item] = typeof(ItemReward),
            [RewardType.Loot] = typeof(LootReward),
            [RewardType.LootTable] = typeof(LootTableReward),
            [RewardType.Xp] = typeof(XpReward),
            [RewardType.Command] = typeof(CommandReward),
            [RewardType.Custom] = typeof(CustomReward),
        };

    public static IReadOnlyDictionary<RewardType, Type> Mappings => RewardTypeMappings;

    public static bool TryGetType(RewardType rewardType, out Type rewardClass) =>
        RewardTypeMappings.TryGetValue(rewardType, out rewardClass);

    public static Type GetType(RewardType rewardType)
    {
        if (TryGetType(rewardType, out var rewardClass))
        {
            return rewardClass;
        }

        throw new ArgumentOutOfRangeException(nameof(rewardType), rewardType, "Unknown reward type.");
    }
}
