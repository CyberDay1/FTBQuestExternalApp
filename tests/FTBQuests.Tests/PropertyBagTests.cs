using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="PropertyBagTests.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using FTBQuests.Codecs.Model;
using Newtonsoft.Json.Linq;
using Xunit;

namespace FTBQuests.Tests;

public class PropertyBagTests
{
    [Fact]
    public void Extra_IsInitialized()
    {
        var bag = new PropertyBag();

        Assert.NotNull(bag.Extra);
        Assert.Empty(bag.Extra);
    }

    [Fact]
    public void Add_StoresValue()
    {
        var bag = new PropertyBag();
        var token = JToken.FromObject(123);

        bag.Add("test", token);

        Assert.True(bag.TryGetValue("test", out var value));
        Assert.Equal(token, value);
    }
}

