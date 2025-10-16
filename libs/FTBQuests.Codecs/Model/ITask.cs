using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="ITask.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

namespace FTBQuestExternalApp.Codecs.Model;

public interface ITask : IExtraAware
{
    string TypeId { get; }
}
