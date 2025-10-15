using System;
using System.Collections.Generic;

namespace FTBQuestExternalApp.Codecs.Model;

public class Quest : IExtraAware
{
    private readonly List<string> propertyOrder = new();

    public Guid Id { get; set; }

    public PropertyBag Extra { get; } = new();

    internal IList<string> PropertyOrder => propertyOrder;
}
