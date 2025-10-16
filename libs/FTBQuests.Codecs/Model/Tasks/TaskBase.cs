using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="TaskBase.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.Generic;

namespace FTBQuests.Codecs.Model;

public abstract class TaskBase : ITask
{
    private readonly List<string> propertyOrder = new();
    private readonly HashSet<string> knownProperties = new(StringComparer.OrdinalIgnoreCase);
    private readonly string canonicalTypeId;
    private string? customTypeId;

    protected TaskBase(string typeId)
    {
        if (string.IsNullOrWhiteSpace(typeId))
        {
            throw new ArgumentException("Type identifier must be provided.", nameof(typeId));
        }

        canonicalTypeId = typeId;
    }

    public string TypeId => customTypeId ?? canonicalTypeId;

    public PropertyBag Extra { get; } = new();

    internal IList<string> PropertyOrder => propertyOrder;

    internal IEnumerable<string> KnownProperties => knownProperties;

    internal string CanonicalTypeId => canonicalTypeId;

    internal void SetPropertyOrder(IEnumerable<string> order)
    {
        propertyOrder.Clear();
        propertyOrder.AddRange(order);
    }

    internal void ClearKnownProperties()
    {
        knownProperties.Clear();
    }

    internal void SetTypeId(string? typeId)
    {
        customTypeId = string.IsNullOrEmpty(typeId) ? null : typeId;
    }

    internal void MarkKnownProperty(string propertyName)
    {
        if (!string.IsNullOrWhiteSpace(propertyName))
        {
            knownProperties.Add(propertyName);
        }
    }

    internal bool HasKnownProperty(string propertyName)
    {
        return knownProperties.Contains(propertyName);
    }
}

