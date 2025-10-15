using System;
using System.Linq;
using FTBQuestExternalApp.Codecs.Model;
using FTBQuestExternalApp.Codecs.Serialization;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using Xunit;

namespace FTBQuests.Tests;

public class QuestCodecTests
{
    private const string GoldenJson = "{\n  \"title\": \"Collect Apples\",\n  \"id\": \"33333333-3333-3333-3333-333333333333\",\n  \"subtitle\": \"Gather resources\",\n  \"icon\": \"minecraft:apple\",\n  \"x\": 10,\n  \"y\": -2,\n  \"page\": 1,\n  \"dependencies\": [\n    \"11111111-1111-1111-1111-111111111111\"\n  ],\n  \"tasks\": [\n    {\n      \"type\": \"item\",\n      \"item\": \"minecraft:apple\",\n      \"count\": 4\n    },\n    {\n      \"type\": \"custom_mod:visit_biome\",\n      \"biome\": \"minecraft:plains\"\n    }\n  ],\n  \"rewards\": [\n    {\n      \"type\": \"xp\",\n      \"amount\": 150\n    },\n    {\n      \"type\": \"loot\",\n      \"table\": \"minecraft:chests/simple_dungeon\"\n    }\n  ],\n  \"metadata\": {\n    \"difficulty\": \"medium\"\n  }\n}";

    private static readonly JsonSerializerSettings Settings = new()
    {
        Converters = { new QuestConverter() },
    };

    [Fact]
    public void Deserialize_LoadsKnownAndUnknownFields()
    {
        var quest = JsonConvert.DeserializeObject<Quest>(GoldenJson, Settings)!;

        Assert.Equal(Guid.Parse("33333333-3333-3333-3333-333333333333"), quest.Id);
        Assert.Equal("Collect Apples", quest.Title);
        Assert.Equal("Gather resources", quest.Subtitle);
        Assert.Equal(new Identifier("minecraft:apple"), quest.IconId);
        Assert.Equal(10, quest.PositionX);
        Assert.Equal(-2, quest.PositionY);
        Assert.Equal(1, quest.Page);
        Assert.Single(quest.Dependencies);
        Assert.Equal(Guid.Parse("11111111-1111-1111-1111-111111111111"), quest.Dependencies[0]);

        Assert.Equal(2, quest.Tasks.Count);
        var itemTask = Assert.IsType<ItemTask>(quest.Tasks[0]);
        Assert.Equal(new Identifier("minecraft:apple"), itemTask.ItemId);
        Assert.Equal(4, itemTask.Count);
        Assert.Null(itemTask.Nbt);
        Assert.False(itemTask.Extra.TryGetValue("item", out _));
        Assert.False(itemTask.Extra.TryGetValue("count", out _));

        Assert.IsType<UnknownTask>(quest.Tasks[1]);
        Assert.Equal("custom_mod:visit_biome", quest.Tasks[1].TypeId);
        Assert.True(quest.Tasks[1].Extra.TryGetValue("biome", out var biomeToken));
        Assert.Equal("minecraft:plains", biomeToken!.Value<string>());

        Assert.Equal(2, quest.Rewards.Count);
        var xpReward = Assert.IsType<XpReward>(quest.Rewards[0]);
        Assert.Equal(150, xpReward.Amount);
        Assert.False(xpReward.Levels);
        Assert.Empty(xpReward.Extra.Extra);

        var lootReward = Assert.IsType<LootReward>(quest.Rewards[1]);
        Assert.Equal(new Identifier("minecraft:chests/simple_dungeon"), lootReward.LootTable);
        Assert.Empty(lootReward.Extra.Extra);

        Assert.True(quest.Extra.TryGetValue("metadata", out var metadataToken));
        Assert.Equal("medium", metadataToken!["difficulty"]!.Value<string>());
    }

    [Fact]
    public void RoundTrip_PreservesOrderAndData()
    {
        var quest = JsonConvert.DeserializeObject<Quest>(GoldenJson, Settings)!;

        var serialized = JsonConvert.SerializeObject(quest, Formatting.None, Settings);
        var roundTripped = JsonConvert.DeserializeObject<Quest>(serialized, Settings)!;

        var originalObject = JObject.Parse(GoldenJson);
        var serializedObject = JObject.Parse(serialized);

        var originalOrder = originalObject.Properties().Select(p => p.Name).ToArray();
        var serializedOrder = serializedObject.Properties().Select(p => p.Name).ToArray();
        Assert.Equal(originalOrder, serializedOrder);

        var originalTaskOrders = ((JArray)originalObject["tasks"]!).Select(t => ((JObject)t).Properties().Select(p => p.Name).ToArray()).ToArray();
        var serializedTaskOrders = ((JArray)serializedObject["tasks"]!).Select(t => ((JObject)t).Properties().Select(p => p.Name).ToArray()).ToArray();
        Assert.Equal(originalTaskOrders, serializedTaskOrders);

        var originalRewardOrders = ((JArray)originalObject["rewards"]!).Select(t => ((JObject)t).Properties().Select(p => p.Name).ToArray()).ToArray();
        var serializedRewardOrders = ((JArray)serializedObject["rewards"]!).Select(t => ((JObject)t).Properties().Select(p => p.Name).ToArray()).ToArray();
        Assert.Equal(originalRewardOrders, serializedRewardOrders);

        Assert.Equal(quest.Id, roundTripped.Id);
        Assert.Equal(quest.Title, roundTripped.Title);
        Assert.Equal(quest.Subtitle, roundTripped.Subtitle);
        Assert.Equal(quest.IconId, roundTripped.IconId);
        Assert.Equal(quest.PositionX, roundTripped.PositionX);
        Assert.Equal(quest.PositionY, roundTripped.PositionY);
        Assert.Equal(quest.Page, roundTripped.Page);
        Assert.Equal(quest.Dependencies, roundTripped.Dependencies);

        Assert.True(roundTripped.Extra.TryGetValue("metadata", out var metadataToken));
        Assert.Equal("medium", metadataToken!["difficulty"]!.Value<string>());

        Assert.Equal(quest.Tasks.Count, roundTripped.Tasks.Count);
        var roundTrippedItem = Assert.IsType<ItemTask>(roundTripped.Tasks[0]);
        Assert.Equal(new Identifier("minecraft:apple"), roundTrippedItem.ItemId);
        Assert.Equal(4, roundTrippedItem.Count);
        Assert.Equal(quest.Tasks[1].TypeId, roundTripped.Tasks[1].TypeId);
        Assert.True(roundTripped.Tasks[1].Extra.TryGetValue("biome", out var biomeToken));
        Assert.Equal("minecraft:plains", biomeToken!.Value<string>());

        Assert.Equal(quest.Rewards.Count, roundTripped.Rewards.Count);

        var roundTrippedXp = Assert.IsType<XpReward>(roundTripped.Rewards[0]);
        Assert.Equal(150, roundTrippedXp.Amount);
        Assert.False(roundTrippedXp.Levels);

        var roundTrippedLoot = Assert.IsType<LootReward>(roundTripped.Rewards[1]);
        Assert.Equal(new Identifier("minecraft:chests/simple_dungeon"), roundTrippedLoot.LootTable);
    }
}
