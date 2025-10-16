using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="IdentifierTests.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using FTBQuests.Codecs.Model;
using Xunit;

namespace FTBQuests.Tests;

public class IdentifierTests
{
    [Theory]
    [InlineData("minecraft:stone")]
    [InlineData("ftbquests:quests/root")]
    [InlineData("ftbquests.custom:some_path")] 
    public void Constructor_AllowsValidValues(string value)
    {
        var identifier = new Identifier(value);

        Assert.Equal(value, identifier.Value);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData(" ")]
    [InlineData("invalid")]
    [InlineData(":path")]
    [InlineData("namespace:")]
    [InlineData("NameSpace:Path")] // uppercase letters are not allowed
    [InlineData("namespace:path with spaces")]
    public void Constructor_RejectsInvalidValues(string? value)
    {
        var exception = Assert.Throws<ArgumentException>(() => new Identifier(value!));

        Assert.Contains("namespace:path", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void ImplicitConversion_ToString_ReturnsValue()
    {
        Identifier identifier = new("minecraft:stone");

        string value = identifier;

        Assert.Equal("minecraft:stone", value);
    }

    [Fact]
    public void ImplicitConversion_FromString_Validates()
    {
        Assert.Throws<ArgumentException>(() =>
        {
            Identifier identifier = "invalid";
        });
    }

    [Theory]
    [InlineData("minecraft:stone", true)]
    [InlineData("ftbquests:quests/root", true)]
    [InlineData("minecraft", false)]
    [InlineData("minecraft:" , false)]
    [InlineData(":stone", false)]
    [InlineData("minecraft:Stone", false)]
    public void IsValid_EvaluatesFormat(string value, bool expected)
    {
        Assert.Equal(expected, Identifier.IsValid(value));
    }
}

