using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="PresetStoreTests.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using FTBQuests.Codecs.Model;
using FTBQuests.IO;
using Newtonsoft.Json.Linq;
using Xunit;

namespace FTBQuests.Tests;

public class PresetStoreTests
{
    [Fact]
    public async Task SaveAndLoad_RoundTripsQuestPack()
    {
        using var fixture = new PresetStoreFixture();
        var store = fixture.Store;

        var pack = CreateQuestPack();

        await store.SaveAsync(1, "Alpha", pack);

        var loaded = await store.LoadAsync(1);
        Assert.NotNull(loaded);
        Assert.Equal("Alpha", loaded?.Name);

        var loadedPack = loaded?.Pack;
        Assert.NotNull(loadedPack);
        Assert.Single(loadedPack!.Chapters);
        Assert.True(loadedPack.Metadata.TryGetValue("meta.json", out var metadata));
        Assert.Equal(42, metadata?["value"]?.Value<int>());
    }

    [Fact]
    public async Task DeleteAsync_RemovesPreset()
    {
        using var fixture = new PresetStoreFixture();
        var store = fixture.Store;
        var pack = CreateQuestPack();

        await store.SaveAsync(1, "Alpha", pack);
        await store.DeleteAsync(1);

        var loaded = await store.LoadAsync(1);
        Assert.Null(loaded);
    }

    [Fact]
    public async Task Rename_UpdatesNameAndTimestamp()
    {
        using var fixture = new PresetStoreFixture();
        var store = fixture.Store;
        var pack = CreateQuestPack();

        await store.SaveAsync(1, "Alpha", pack);
        var originalInfo = await store.ListDetailedAsync();
        var originalTimestamp = originalInfo.Single(info => info.Slot == 1).LastModifiedUtc;
        Assert.True(originalTimestamp.HasValue);

        var existing = await store.LoadAsync(1);
        Assert.NotNull(existing);

        await store.SaveAsync(1, "Beta", existing!.Value.Pack);

        var info = await store.ListDetailedAsync();
        var slotInfo = info.Single(i => i.Slot == 1);
        Assert.Equal("Beta", slotInfo.Name);
        var updatedTimestamp = slotInfo.LastModifiedUtc;
        Assert.True(updatedTimestamp.HasValue);
        Assert.True(updatedTimestamp.Value >= originalTimestamp.Value);
    }

    [Fact]
    public async Task SaveAsync_ThrowsWhenSlotOutOfRange()
    {
        using var fixture = new PresetStoreFixture();
        var store = fixture.Store;
        var pack = CreateQuestPack();

        await Assert.ThrowsAsync<ArgumentOutOfRangeException>(() => store.SaveAsync(0, "Test", pack));
        await Assert.ThrowsAsync<ArgumentOutOfRangeException>(() => store.SaveAsync(PresetStore.MaxSlots + 1, "Test", pack));
    }

    [Fact]
    public async Task ListAsync_ReturnsAllSlots()
    {
        using var fixture = new PresetStoreFixture();
        var store = fixture.Store;
        var pack = CreateQuestPack();

        await store.SaveAsync(2, "Alpha", pack);

        var slots = await store.ListAsync();
        Assert.Equal(PresetStore.MaxSlots, slots.Count);
        Assert.Contains(slots, tuple => tuple.Slot == 2 && tuple.Name == "Alpha");
    }

    private static FTBQuests.IO.QuestPack CreateQuestPack()
    {
        var pack = new FTBQuests.IO.QuestPack();
        pack.Metadata.Add("meta.json", JToken.FromObject(new { value = 42 }));

        var chapter = pack.CreateChapter();
        chapter.Title = "Test Chapter";
        chapter.Quests.Add(new Quest { Title = "First Quest" });

        return pack;
    }

    private sealed class PresetStoreFixture : IDisposable
    {
        private readonly string directory;

        public PresetStoreFixture()
        {
            directory = Path.Combine(Path.GetTempPath(), "PresetStoreTests", Guid.NewGuid().ToString("N"));
            Store = new PresetStore(directory);
        }

        public PresetStore Store { get; }

        public void Dispose()
        {
            if (Directory.Exists(directory))
            {
                Directory.Delete(directory, recursive: true);
            }
        }
    }
}

