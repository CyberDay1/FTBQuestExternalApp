using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="ILayoutNode.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

namespace FTBQuestExternalApp.Codecs.Model;

public interface ILayoutNode
{
    int PositionX { get; }

    int PositionY { get; }

    int Page { get; }

    Identifier IconId { get; }
}
