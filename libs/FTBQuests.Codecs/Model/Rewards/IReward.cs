// <copyright file="IReward.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using FTBQuestExternalApp.Codecs.Enums;

namespace FTBQuestExternalApp.Codecs.Model;

public interface IReward : IExtraAware
{
    RewardType RewardType { get; }

    string TypeId { get; }
}
