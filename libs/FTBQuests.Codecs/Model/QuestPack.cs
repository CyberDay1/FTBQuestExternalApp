// <copyright file="QuestPack.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System.Collections.Generic;

namespace FTBQuestExternalApp.Codecs.Model;

/// <summary>
/// Represents the root quest pack containing all chapters and quests.
/// </summary>
public class QuestPack : IExtraAware
{
    /// <summary>
    /// Gets the collection of chapters included in the pack.
    /// </summary>
    public List<Chapter> Chapters { get; } = new();

    /// <summary>
    /// Gets the bag of additional metadata preserved during serialization.
    /// </summary>
    public PropertyBag Extra { get; } = new();
}
