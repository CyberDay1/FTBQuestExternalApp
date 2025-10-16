using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="TaskCodecTests.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System.Linq;
using FTBQuests.Codecs.Enums;
using FTBQuests.Codecs.Model;
using FTBQuests.Codecs.Serialization;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using Xunit;

namespace FTBQuests.Tests;

public class TaskCodecTests
{
    private static readonly JsonSerializerSettings Settings = new()
    {
        Converters = { new QuestConverter() },
    };

    [Fact]
    public void ItemTask_RoundTrip_PreservesKnownFields()
    {
        const string taskJson = "{\"type\":\"item\",\"item\":\"minecraft:apple\",\"count\":3,\"nbt\":\"{}\",\"rarity\":\"rare\"}";

        var quest = Deserialize(taskJson);
        var itemTask = Assert.IsType<ItemTask>(quest.Tasks.Single());
        Assert.Equal(new Identifier("minecraft:apple"), itemTask.ItemId);
        Assert.Equal(3, itemTask.Count);
        Assert.Equal("{}", itemTask.Nbt);
        Assert.True(itemTask.Extra.TryGetValue("rarity", out var rarityToken));
        Assert.Equal("rare", rarityToken!.Value<string>());
        Assert.False(itemTask.Extra.TryGetValue("item", out _));
        Assert.False(itemTask.Extra.TryGetValue("count", out _));

        var serialized = Serialize(quest);
        var taskObject = ExtractSingleTask(serialized);
        Assert.Equal("item", taskObject["type"]!.Value<string>());
        Assert.Equal("minecraft:apple", taskObject["item"]!.Value<string>());
        Assert.Equal(3, taskObject["count"]!.Value<int>());
        Assert.Equal("{}", taskObject["nbt"]!.Value<string>());
        Assert.Equal("rare", taskObject["rarity"]!.Value<string>());
    }

    [Fact]
    public void AdvancementTask_RoundTrip_PreservesKnownFields()
    {
        const string taskJson = "{\"type\":\"advancement\",\"advancement\":\"minecraft:story/root\",\"note\":\"reach spawn\"}";

        var quest = Deserialize(taskJson);
        var advancementTask = Assert.IsType<AdvancementTask>(quest.Tasks.Single());
        Assert.Equal(new Identifier("minecraft:story/root"), advancementTask.AdvancementId);
        Assert.True(advancementTask.Extra.TryGetValue("note", out var noteToken));
        Assert.Equal("reach spawn", noteToken!.Value<string>());

        var serialized = Serialize(quest);
        var taskObject = ExtractSingleTask(serialized);
        Assert.Equal("advancement", taskObject["type"]!.Value<string>());
        Assert.Equal("minecraft:story/root", taskObject["advancement"]!.Value<string>());
        Assert.Equal("reach spawn", taskObject["note"]!.Value<string>());
    }

    [Fact]
    public void KillTask_RoundTrip_PreservesKnownFields()
    {
        const string taskJson = "{\"type\":\"kill\",\"entity\":\"minecraft:zombie\",\"value\":5,\"weapon\":\"minecraft:bow\"}";

        var quest = Deserialize(taskJson);
        var killTask = Assert.IsType<KillTask>(quest.Tasks.Single());
        Assert.Equal(new Identifier("minecraft:zombie"), killTask.EntityId);
        Assert.Equal(5, killTask.Amount);
        Assert.True(killTask.Extra.TryGetValue("weapon", out var weaponToken));
        Assert.Equal("minecraft:bow", weaponToken!.Value<string>());

        var serialized = Serialize(quest);
        var taskObject = ExtractSingleTask(serialized);
        Assert.Equal("kill", taskObject["type"]!.Value<string>());
        Assert.Equal("minecraft:zombie", taskObject["entity"]!.Value<string>());
        Assert.Equal(5, taskObject["value"]!.Value<int>());
        Assert.Equal("minecraft:bow", taskObject["weapon"]!.Value<string>());
    }

    [Fact]
    public void LocationTask_RoundTrip_PreservesKnownFields()
    {
        const string taskJson = "{\"type\":\"location\",\"x\":10,\"y\":64,\"z\":-5,\"dim\":\"minecraft:overworld\",\"radius\":3,\"message\":\"stand here\"}";

        var quest = Deserialize(taskJson);
        var locationTask = Assert.IsType<LocationTask>(quest.Tasks.Single());
        Assert.Equal(10, locationTask.X);
        Assert.Equal(64, locationTask.Y);
        Assert.Equal(-5, locationTask.Z);
        Assert.Equal("minecraft:overworld", locationTask.Dimension);
        Assert.Equal(3, locationTask.Radius);
        Assert.True(locationTask.Extra.TryGetValue("message", out var messageToken));
        Assert.Equal("stand here", messageToken!.Value<string>());

        var serialized = Serialize(quest);
        var taskObject = ExtractSingleTask(serialized);
        Assert.Equal("location", taskObject["type"]!.Value<string>());
        Assert.Equal(10, taskObject["x"]!.Value<int>());
        Assert.Equal(64, taskObject["y"]!.Value<int>());
        Assert.Equal(-5, taskObject["z"]!.Value<int>());
        Assert.Equal("minecraft:overworld", taskObject["dim"]!.Value<string>());
        Assert.Equal(3, taskObject["radius"]!.Value<int>());
        Assert.Equal("stand here", taskObject["message"]!.Value<string>());
    }

    [Fact]
    public void XpTask_RoundTrip_PreservesKnownFields()
    {
        const string taskJson = "{\"type\":\"xp\",\"value\":42,\"levels\":true,\"source\":\"quests\"}";

        var quest = Deserialize(taskJson);
        var xpTask = Assert.IsType<XpTask>(quest.Tasks.Single());
        Assert.Equal(42, xpTask.Amount);
        Assert.True(xpTask.Levels);
        Assert.True(xpTask.Extra.TryGetValue("source", out var sourceToken));
        Assert.Equal("quests", sourceToken!.Value<string>());

        var serialized = Serialize(quest);
        var taskObject = ExtractSingleTask(serialized);
        Assert.Equal("xp", taskObject["type"]!.Value<string>());
        Assert.Equal(42, taskObject["value"]!.Value<int>());
        Assert.True(taskObject["levels"]!.Value<bool>());
        Assert.Equal("quests", taskObject["source"]!.Value<string>());
    }

    [Fact]
    public void NbtTask_RoundTrip_PreservesKnownFields()
    {
        const string taskJson = "{\"type\":\"nbt\",\"target\":\"minecraft:diamond_pickaxe\",\"nbt\":\"{\\\"Damage\\\":0}\",\"label\":\"fresh tool\"}";

        var quest = Deserialize(taskJson);
        var nbtTask = Assert.IsType<NbtTask>(quest.Tasks.Single());
        Assert.Equal(new Identifier("minecraft:diamond_pickaxe"), nbtTask.TargetId);
        Assert.Equal("{\"Damage\":0}", nbtTask.RequiredNbt);
        Assert.True(nbtTask.Extra.TryGetValue("label", out var labelToken));
        Assert.Equal("fresh tool", labelToken!.Value<string>());

        var serialized = Serialize(quest);
        var taskObject = ExtractSingleTask(serialized);
        Assert.Equal("nbt", taskObject["type"]!.Value<string>());
        Assert.Equal("minecraft:diamond_pickaxe", taskObject["target"]!.Value<string>());
        Assert.Equal("{\"Damage\":0}", taskObject["nbt"]!.Value<string>());
        Assert.Equal("fresh tool", taskObject["label"]!.Value<string>());
    }

    [Fact]
    public void CommandTask_RoundTrip_PreservesKnownFields()
    {
        const string taskJson = "{\"type\":\"command\",\"command\":\"say hello\",\"delay\":10}";

        var quest = Deserialize(taskJson);
        var commandTask = Assert.IsType<CommandTask>(quest.Tasks.Single());
        Assert.Equal("say hello", commandTask.Command);
        Assert.True(commandTask.Extra.TryGetValue("delay", out var delayToken));
        Assert.Equal(10, delayToken!.Value<int>());

        var serialized = Serialize(quest);
        var taskObject = ExtractSingleTask(serialized);
        Assert.Equal("command", taskObject["type"]!.Value<string>());
        Assert.Equal("say hello", taskObject["command"]!.Value<string>());
        Assert.Equal(10, taskObject["delay"]!.Value<int>());
    }

    [Fact]
    public void TaskDiscriminator_MapsTypes()
    {
        Assert.Equal(typeof(ItemTask), TaskDiscriminator.GetClrType(TaskType.Item));
        Assert.True(TaskDiscriminator.TryGetTaskType(typeof(LocationTask), out var locationType));
        Assert.Equal(TaskType.Location, locationType);
        Assert.True(TaskDiscriminator.TryGetTaskType("xp", out var xpType));
        Assert.Equal(TaskType.Xp, xpType);

        var created = TaskDiscriminator.Create("item");
        Assert.IsType<ItemTask>(created);

        var unknown = TaskDiscriminator.Create("custom_mod:visit_biome");
        Assert.IsType<UnknownTask>(unknown);
        Assert.Equal("custom_mod:visit_biome", unknown.TypeId);
    }

    private static Quest Deserialize(string taskJson)
    {
        const int questId = 123;
        var questJson = $"{{\"id\":{questId},\"title\":\"Test\",\"tasks\":[{taskJson}]}}";
        return JsonConvert.DeserializeObject<Quest>(questJson, Settings)!;
    }

    private static string Serialize(Quest quest)
    {
        return JsonConvert.SerializeObject(quest, Formatting.None, Settings);
    }

    private static JObject ExtractSingleTask(string questJson)
    {
        var root = JObject.Parse(questJson);
        return (JObject)root["tasks"]!.Single()!;
    }
}

