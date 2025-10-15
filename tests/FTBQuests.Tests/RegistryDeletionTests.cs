using System;
using System.Collections.Generic;
using FTBQuestExternalApp.Codecs.Model;
using FTBQuests.Registry;
using FTBQuests.Registry.Model;
using Xunit;

namespace FTBQuests.Tests;

public sealed class RegistryDeletionTests
{
    [Fact]
    public void RemoveItem_RemovesFromLookups()
    {
        var items = new List<RegistryItem>
        {
            new("minecraft:stone", "Stone", null, "minecraft"),
            new("minecraft:dirt", "Dirt", null, "minecraft"),
            new("mod:item", "Widget", null, "mod"),
        };

        var tags = new Dictionary<string, IReadOnlyCollection<string>>(StringComparer.OrdinalIgnoreCase)
        {
            ["minecraft:blocks"] = new[] { "minecraft:stone", "minecraft:dirt" },
            ["mod:items"] = new[] { "mod:item", "minecraft:stone" },
        };

        var database = new RegistryDatabase(items, tags);

        bool removed = database.RemoveItem(new Identifier("minecraft:stone"));

        Assert.True(removed);
        Assert.False(database.TryGetByIdentifier("minecraft:stone", out _));
        Assert.DoesNotContain(database.Items, item => item.Id == "minecraft:stone");
        Assert.All(database.GetByTag("minecraft:blocks"), item => Assert.NotEqual("minecraft:stone", item.Id));
        Assert.All(database.GetByTag("mod:items"), item => Assert.NotEqual("minecraft:stone", item.Id));
        Assert.All(database.GetItemsByMod("minecraft"), item => Assert.NotEqual("minecraft:stone", item.Id));
    }

    [Fact]
    public void RemoveItemsByMod_RemovesAllEntries()
    {
        var items = new List<RegistryItem>
        {
            new("minecraft:stone", "Stone", null, "minecraft"),
            new("minecraft:dirt", "Dirt", null, "minecraft"),
            new("mod:item", "Widget", null, "mod"),
        };

        var tags = new Dictionary<string, IReadOnlyCollection<string>>(StringComparer.OrdinalIgnoreCase)
        {
            ["minecraft:all"] = new[] { "minecraft:stone", "minecraft:dirt", "mod:item" },
            ["minecraft:blocks"] = new[] { "minecraft:stone" },
        };

        var database = new RegistryDatabase(items, tags);

        int removed = database.RemoveItemsByMod("minecraft");

        Assert.Equal(2, removed);
        Assert.False(database.TryGetByIdentifier("minecraft:stone", out _));
        Assert.False(database.TryGetByIdentifier("minecraft:dirt", out _));
        Assert.Empty(database.GetItemsByMod("minecraft"));
        Assert.DoesNotContain(database.Items, item => item.SourceModId == "minecraft");
        Assert.Empty(database.GetByTag("minecraft:blocks"));
        Assert.Single(database.GetByTag("minecraft:all"));
        Assert.Equal("mod", Assert.Single(database.GetModIdentifiers()));
    }
}
