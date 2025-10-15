using System;
using System.Linq;
using FTBQuestExternalApp.Codecs.Enums;
using FTBQuestExternalApp.Codecs.Model;
using FTBQuestExternalApp.Codecs.Serialization;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using Xunit;

namespace FTBQuests.Tests;

public class RewardSerializationTests
{
    private static readonly JsonSerializerSettings Settings = new()
    {
        Converters = { new QuestConverter() },
    };

    [Fact]
    public void ItemReward_RoundTrips()
    {
        var rewardJson = CreateQuestJson(
            new JObject
            {
                ["type"] = "item",
                ["item"] = "minecraft:apple",
                ["count"] = 3,
                ["nbt"] = "{\"foo\":1}",
            });

        var quest = JsonConvert.DeserializeObject<Quest>(rewardJson, Settings)!;
        var reward = Assert.IsType<ItemReward>(quest.Rewards.Single());
        Assert.Equal(new Identifier("minecraft:apple"), reward.ItemId);
        Assert.Equal(3, reward.Count);
        Assert.Equal("{\"foo\":1}", reward.Nbt);
        Assert.Empty(reward.Extra.Extra);

        var serialized = JsonConvert.SerializeObject(quest, Formatting.None, Settings);
        var roundTripped = JsonConvert.DeserializeObject<Quest>(serialized, Settings)!;
        var roundTrippedReward = Assert.IsType<ItemReward>(roundTripped.Rewards.Single());
        Assert.Equal(reward.ItemId, roundTrippedReward.ItemId);
        Assert.Equal(reward.Count, roundTrippedReward.Count);
        Assert.Equal(reward.Nbt, roundTrippedReward.Nbt);
    }

    [Fact]
    public void LootReward_RoundTrips()
    {
        var rewardJson = CreateQuestJson(
            new JObject
            {
                ["type"] = "loot",
                ["table"] = "minecraft:chests/village/village_armorer",
            });

        var quest = JsonConvert.DeserializeObject<Quest>(rewardJson, Settings)!;
        var reward = Assert.IsType<LootReward>(quest.Rewards.Single());
        Assert.Equal(new Identifier("minecraft:chests/village/village_armorer"), reward.LootTable);
        Assert.Empty(reward.Extra.Extra);

        var serialized = JsonConvert.SerializeObject(quest, Formatting.None, Settings);
        var roundTripped = JsonConvert.DeserializeObject<Quest>(serialized, Settings)!;
        var roundTrippedReward = Assert.IsType<LootReward>(roundTripped.Rewards.Single());
        Assert.Equal(reward.LootTable, roundTrippedReward.LootTable);
    }

    [Fact]
    public void LootTableReward_RoundTrips()
    {
        var rewardJson = CreateQuestJson(
            new JObject
            {
                ["type"] = "loot_table",
                ["table_name"] = "starter_items",
            });

        var quest = JsonConvert.DeserializeObject<Quest>(rewardJson, Settings)!;
        var reward = Assert.IsType<LootTableReward>(quest.Rewards.Single());
        Assert.Equal("starter_items", reward.TableName);
        Assert.Empty(reward.Extra.Extra);

        var serialized = JsonConvert.SerializeObject(quest, Formatting.None, Settings);
        var roundTripped = JsonConvert.DeserializeObject<Quest>(serialized, Settings)!;
        var roundTrippedReward = Assert.IsType<LootTableReward>(roundTripped.Rewards.Single());
        Assert.Equal(reward.TableName, roundTrippedReward.TableName);
    }

    [Fact]
    public void XpReward_RoundTrips()
    {
        var rewardJson = CreateQuestJson(
            new JObject
            {
                ["type"] = "xp",
                ["amount"] = 500,
                ["levels"] = true,
            });

        var quest = JsonConvert.DeserializeObject<Quest>(rewardJson, Settings)!;
        var reward = Assert.IsType<XpReward>(quest.Rewards.Single());
        Assert.Equal(500, reward.Amount);
        Assert.True(reward.Levels);
        Assert.Empty(reward.Extra.Extra);

        var serialized = JsonConvert.SerializeObject(quest, Formatting.None, Settings);
        var roundTripped = JsonConvert.DeserializeObject<Quest>(serialized, Settings)!;
        var roundTrippedReward = Assert.IsType<XpReward>(roundTripped.Rewards.Single());
        Assert.Equal(reward.Amount, roundTrippedReward.Amount);
        Assert.Equal(reward.Levels, roundTrippedReward.Levels);
    }

    [Fact]
    public void CommandReward_RoundTrips()
    {
        var rewardJson = CreateQuestJson(
            new JObject
            {
                ["type"] = "command",
                ["command"] = "/say hello",
            });

        var quest = JsonConvert.DeserializeObject<Quest>(rewardJson, Settings)!;
        var reward = Assert.IsType<CommandReward>(quest.Rewards.Single());
        Assert.Equal("/say hello", reward.Command);
        Assert.Empty(reward.Extra.Extra);

        var serialized = JsonConvert.SerializeObject(quest, Formatting.None, Settings);
        var roundTripped = JsonConvert.DeserializeObject<Quest>(serialized, Settings)!;
        var roundTrippedReward = Assert.IsType<CommandReward>(roundTripped.Rewards.Single());
        Assert.Equal(reward.Command, roundTrippedReward.Command);
    }

    private static string CreateQuestJson(JObject reward)
    {
        var quest = new JObject
        {
            ["title"] = "Test",
            ["id"] = "00000000-0000-0000-0000-000000000001",
            ["rewards"] = new JArray(reward),
        };

        return quest.ToString(Formatting.None);
    }

    [Theory]
    [InlineData(RewardType.Item, typeof(ItemReward))]
    [InlineData(RewardType.Loot, typeof(LootReward))]
    [InlineData(RewardType.LootTable, typeof(LootTableReward))]
    [InlineData(RewardType.Xp, typeof(XpReward))]
    [InlineData(RewardType.Command, typeof(CommandReward))]
    [InlineData(RewardType.Custom, typeof(CustomReward))]
    public void RewardDiscriminator_ReturnsExpectedTypes(RewardType rewardType, Type expectedType)
    {
        Assert.True(RewardDiscriminator.TryGetType(rewardType, out var mappedType));
        Assert.Equal(expectedType, mappedType);
        Assert.Equal(expectedType, RewardDiscriminator.GetType(rewardType));
    }
}
