// <copyright file="Chapter.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System.Collections.Generic;

namespace FTBQuestExternalApp.Codecs.Model;

public class Chapter : IExtraAware
{
    private readonly List<string> propertyOrder = new();

    public long Id { get; set; }

    public string Title { get; set; } = string.Empty;

    public string? Description { get; set; }

    public Identifier? IconId { get; set; }

    public List<Quest> Quests { get; set; } = new();

    public PropertyBag Extra { get; } = new();

    internal IList<string> PropertyOrder => propertyOrder;

    internal void SetPropertyOrder(IEnumerable<string> order)
    {
        propertyOrder.Clear();
        propertyOrder.AddRange(order);
    }
}
