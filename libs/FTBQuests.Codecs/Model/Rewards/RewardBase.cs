using System;
using System.Collections.Generic;

namespace FTBQuestExternalApp.Codecs.Model;

public abstract class RewardBase : IReward
{
    private readonly List<string> propertyOrder = new();
    private readonly string canonicalTypeId;
    private string? customTypeId;

    protected RewardBase(string typeId)
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

    internal string CanonicalTypeId => canonicalTypeId;

    internal void SetPropertyOrder(IEnumerable<string> order)
    {
        propertyOrder.Clear();
        propertyOrder.AddRange(order);
    }

    internal void SetTypeId(string? typeId)
    {
        customTypeId = string.IsNullOrEmpty(typeId) ? null : typeId;
    }
}
