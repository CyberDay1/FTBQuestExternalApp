using System;
using System.Linq;
using FTBQuestExternalApp.Codecs.Model;
using FTBQuestExternalApp.Codecs.Serialization;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using Xunit;

namespace FTBQuests.Tests;

public class ChapterCodecTests
{
    private const string GoldenJson = "{\n  \"title\": \"First Steps\",\n  \"id\": \"11111111-1111-1111-1111-111111111111\",\n  \"customNumber\": 42,\n  \"description\": \"Learn the basics.\",\n  \"icon\": \"minecraft:book\",\n  \"quests\": [\n    {\n      \"id\": \"22222222-2222-2222-2222-222222222222\"\n    }\n  ],\n  \"metadata\": {\n    \"difficulty\": \"easy\"\n  }\n}";

    private static readonly JsonSerializerSettings Settings = new()
    {
        Converters = { new ChapterConverter() },
    };

    [Fact]
    public void Deserialize_LoadsKnownAndUnknownFields()
    {
        var chapter = JsonConvert.DeserializeObject<Chapter>(GoldenJson, Settings)!;

        Assert.Equal(Guid.Parse("11111111-1111-1111-1111-111111111111"), chapter.Id);
        Assert.Equal("First Steps", chapter.Title);
        Assert.Equal("Learn the basics.", chapter.Description);
        Assert.Equal(new Identifier("minecraft:book"), chapter.IconId);
        Assert.Single(chapter.Quests);
        Assert.True(chapter.Extra.TryGetValue("customNumber", out var customToken));
        Assert.Equal(42, customToken!.Value<int>());
        Assert.True(chapter.Extra.TryGetValue("metadata", out var metadataToken));
        Assert.Equal("easy", metadataToken!["difficulty"]!.Value<string>());
    }

    [Fact]
    public void RoundTrip_PreservesOrderAndData()
    {
        var chapter = JsonConvert.DeserializeObject<Chapter>(GoldenJson, Settings)!;

        var serialized = JsonConvert.SerializeObject(chapter, Formatting.None, Settings);
        var roundTripped = JsonConvert.DeserializeObject<Chapter>(serialized, Settings)!;

        var originalOrder = JObject.Parse(GoldenJson).Properties().Select(p => p.Name).ToArray();
        var serializedOrder = JObject.Parse(serialized).Properties().Select(p => p.Name).ToArray();
        Assert.Equal(originalOrder, serializedOrder);

        Assert.Equal(chapter.Id, roundTripped.Id);
        Assert.Equal(chapter.Title, roundTripped.Title);
        Assert.Equal(chapter.Description, roundTripped.Description);
        Assert.Equal(chapter.IconId, roundTripped.IconId);
        Assert.Equal(chapter.Quests.Count, roundTripped.Quests.Count);
        Assert.True(roundTripped.Extra.TryGetValue("customNumber", out var customToken));
        Assert.Equal(42, customToken!.Value<int>());
        Assert.True(roundTripped.Extra.TryGetValue("metadata", out var metadataToken));
        Assert.Equal("easy", metadataToken!["difficulty"]!.Value<string>());
    }
}
