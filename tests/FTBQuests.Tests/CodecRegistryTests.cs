using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="CodecRegistryTests.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using FTBQuests.Codecs;
using Xunit;

namespace FTBQuests.Tests;

public class CodecRegistryTests
{
    [Fact]
    public void Instance_Is_Not_Null()
    {
        Assert.NotNull(CodecRegistry.Instance);
    }
}
