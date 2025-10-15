// <copyright file="Quest.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System.Collections.Generic;

namespace FTBQuestExternalApp.Codecs.Model;

public class Quest : IExtraAware
{
    private readonly List<string> propertyOrder = new();

    public long Id { get; set; }

    public string Title { get; set; } = string.Empty;

    public string? Subtitle { get; set; }

    public Identifier? IconId { get; set; }

    public List<ITask> Tasks { get; } = new();

    public List<IReward> Rewards { get; } = new();

    public List<long> Dependencies { get; } = new();

    public int PositionX { get; set; }

    public int PositionY { get; set; }

    public int Page { get; set; }

    public PropertyBag Extra { get; } = new();

    internal IList<string> PropertyOrder => propertyOrder;

    internal void SetPropertyOrder(IEnumerable<string> order)
    {
        propertyOrder.Clear();
        propertyOrder.AddRange(order);
    }
}
