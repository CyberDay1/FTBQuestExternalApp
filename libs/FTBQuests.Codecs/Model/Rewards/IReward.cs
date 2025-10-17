using FTBQuests.Core.Model;
using FTBQuests.Core.Enums;using FTBQuests.Assets;// <copyright file="IReward.cs" company="CyberDay1">// Copyright (c) CyberDay1. All rights reserved.// </copyright>using FTBQuests.Codecs.Enums;namespace FTBQuests.Codecs.Model;public interface IReward : IExtraAware{    RewardType RewardType { get; }    string TypeId { get; }}




