using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="RegistrySeedingTests.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.Generic;
using System.IO;
using FTBQuests.Registry;
using FTBQuests.Registry.Model;
using FTBQuests.Registry.Seed;
using Xunit;

namespace FTBQuests.Tests;

public sealed class RegistrySeedingTests
{
    [Fact]
    public void EnsureBaseItems_AddsMissingEntries()
    {
        var database = new RegistryDatabase(Array.Empty<RegistryItem>(), new Dictionary<string, IReadOnlyCollection<string>>(StringComparer.OrdinalIgnoreCase));

        VanillaRegistrySeeder.EnsureBaseItems(database);

        Assert.True(database.TryGetByIdentifier("minecraft:stone", out RegistryItem? stone));
        Assert.NotNull(stone);
        Assert.Equal("minecraft", stone!.SourceModId);

        Assert.True(database.TryGetByIdentifier("minecraft:iron_pickaxe", out RegistryItem? pickaxe));
        Assert.NotNull(pickaxe);
        Assert.Equal("Iron Pickaxe", pickaxe!.DisplayName);
    }

    [Fact]
    public async Task LoadFromProbeAsync_SeedsVanillaItemsWhenMissing()
    {
        string tempDirectory = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());
        Directory.CreateDirectory(tempDirectory);

        try
        {
            string registryDump = """
            {
              "items": [
                {"id": "example:widget", "defaultName": "Widget"}
              ],
              "tags": {}
            }
            """;

            await File.WriteAllTextAsync(Path.Combine(tempDirectory, "registry_dump.json"), registryDump);

            var importer = new RegistryImporter();
            RegistryDatabase database = await importer.LoadFromProbeAsync(tempDirectory);

            Assert.True(database.TryGetByIdentifier("minecraft:stone", out _));
            Assert.True(database.TryGetByIdentifier("minecraft:iron_pickaxe", out _));

            IReadOnlyList<RegistryItem> widgets = database.GetBySourceModId("example");
            Assert.Collection(widgets, item => Assert.Equal("example:widget", item.Id));
        }
        finally
        {
            Directory.Delete(tempDirectory, true);
        }
    }
}
