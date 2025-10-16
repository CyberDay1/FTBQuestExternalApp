using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="Chapter.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System.Collections.Generic;

namespace FTBQuestExternalApp.Codecs.Model;

public class Chapter : IExtraAware
{
    private readonly List<string> propertyOrder = new();
    private readonly List<Quest> quests = new();

    public long Id { get; set; }

    public string Title { get; set; } = string.Empty;

    public string? Description { get; set; }

    public Identifier? IconId { get; set; }

    public IReadOnlyList<Quest> Quests => quests;

    public PropertyBag Extra { get; } = new();

    internal IList<string> PropertyOrder => propertyOrder;

    public void AddQuest(Quest quest)
    {
        quests.Add(quest);
    }

    public void AddQuests(IEnumerable<Quest> quests)
    {
        this.quests.AddRange(quests);
    }

    public void ClearQuests()
    {
        quests.Clear();
    }

    internal void SetPropertyOrder(IEnumerable<string> order)
    {
        propertyOrder.Clear();
        propertyOrder.AddRange(order);
    }
}
