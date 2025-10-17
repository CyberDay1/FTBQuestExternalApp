using FTBQuests.Core.Validation;
using FTBQuests.Codecs;
using FTBQuests.Core.Model;

using FTBQuests.Codecs;
using FTBQuests.Core.Model;



using FTBQuests.Assets;
// <copyright file="PresetStore.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using FTBQuests.Codecs.Model;

using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace FTBQuests.IO;

public sealed class PresetStore
{
    public const int MaxSlots = 5;

    private readonly string presetsDirectory;
    private readonly JsonSerializer serializer;

    public PresetStore()
        : this(GetDefaultDirectory())
    {
    }

    public PresetStore(string directory)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(directory);

        presetsDirectory = Path.GetFullPath(directory);
        Directory.CreateDirectory(presetsDirectory);

        serializer = JsonSerializer.Create(JsonSettings.Create());
    }

    public Task SaveAsync(int slot, string name, FTBQuests.IO.QuestPack pack)
    {
        ValidateSlot(slot);
        ArgumentNullException.ThrowIfNull(pack);

        if (string.IsNullOrWhiteSpace(name))
        {
            throw new ArgumentException("Preset name cannot be empty.", nameof(name));
        }

        return SaveInternalAsync(slot, name.Trim(), pack);
    }

    public async Task<(string Name, FTBQuests.IO.QuestPack Pack)?> LoadAsync(int slot)
    {
        ValidateSlot(slot);

        var path = GetSlotPath(slot);
        if (!File.Exists(path))
        {
            return null;
        }

        var json = await File.ReadAllTextAsync(path).ConfigureAwait(false);
        if (string.IsNullOrWhiteSpace(json))
        {
            return null;
        }

        var rootToken = JObject.Parse(json);
        var payload = rootToken.ToObject<PresetData>(serializer);
        if (payload is null || payload.Data is null)
        {
            return null;
        }

        var pack = new FTBQuests.IO.QuestPack();
        if (payload.Data.Metadata is not null)
        {
            foreach (var kvp in payload.Data.Metadata)
            {
                var metaToken = kvp.Value is null ? JValue.CreateNull() : kvp.Value.DeepClone();
                pack.SetMetadata(kvp.Key, metaToken);
            }
        }

        if (payload.Data.MetadataOrder is not null)
        {
            pack.SetMetadataOrder(payload.Data.MetadataOrder);
        }

        if (payload.Data.Chapters is not null)
        {
            foreach (var chapterEntry in payload.Data.Chapters)
            {
                if (chapterEntry?.Chapter is null)
                {
                    continue;
                }

                var chapter = chapterEntry.Chapter?.ToObject<Chapter>(serializer);
                if (chapter is null)
                {
                    continue;
                }

                var relativePath = chapterEntry.Path ?? string.Empty;
                pack.AddChapter(chapter, relativePath);
            }
        }

        return (payload.Name ?? string.Empty, pack);
    }

    public Task DeleteAsync(int slot)
    {
        ValidateSlot(slot);

        var path = GetSlotPath(slot);
        if (File.Exists(path))
        {
            File.Delete(path);
        }

        return Task.CompletedTask;
    }

    public async Task<IReadOnlyList<(int Slot, string? Name)>> ListAsync()
    {
        var detailed = await ListDetailedAsync().ConfigureAwait(false);
        return detailed.Select(info => (info.Slot, info.Name)).ToList();
    }

    public async Task<IReadOnlyList<PresetSlotInfo>> ListDetailedAsync()
    {
        var results = new List<PresetSlotInfo>(MaxSlots);

        for (var slot = 1; slot <= MaxSlots; slot++)
        {
            var path = GetSlotPath(slot);
            if (!File.Exists(path))
            {
                results.Add(new PresetSlotInfo(slot, null, null));
                continue;
            }

            var json = await File.ReadAllTextAsync(path).ConfigureAwait(false);
            if (string.IsNullOrWhiteSpace(json))
            {
                results.Add(new PresetSlotInfo(slot, null, null));
                continue;
            }

            var token = JObject.Parse(json);
            var header = token.ToObject<PresetHeader>(serializer);
            if (header is null)
            {
                results.Add(new PresetSlotInfo(slot, null, null));
                continue;
            }

            DateTimeOffset? savedAt = header.SavedAtUtc == default ? null : header.SavedAtUtc;
            results.Add(new PresetSlotInfo(slot, header.Name, savedAt));
        }

        return results;
    }

    private static string GetDefaultDirectory()
    {
        var dataRoot = Environment.GetEnvironmentVariable("FTBQUESTEDITOR_DATA");
        if (string.IsNullOrWhiteSpace(dataRoot))
        {
            dataRoot = Path.Combine(AppContext.BaseDirectory, "portable_data");
        }

        var presets = Path.Combine(dataRoot, "presets");
        Directory.CreateDirectory(presets);
        return presets;
    }

    private async Task SaveInternalAsync(int slot, string name, FTBQuests.IO.QuestPack pack)
    {
        var path = GetSlotPath(slot);
        var payload = new PresetData
        {
            Name = name,
            SavedAtUtc = DateTimeOffset.UtcNow,
            Data = CreateSnapshot(pack),
        };

        var token = JObject.FromObject(payload, serializer);
        var json = token.ToString(Formatting.Indented);
        await File.WriteAllTextAsync(path, json).ConfigureAwait(false);
    }

    private PresetSnapshot CreateSnapshot(FTBQuests.IO.QuestPack pack)
    {
        var snapshot = new PresetSnapshot
        {
            Metadata = new Dictionary<string, JToken>(StringComparer.Ordinal),
            MetadataOrder = pack.MetadataOrder?.ToList() ?? new List<string>(),
            Chapters = new List<PresetChapter>(),
        };

        foreach (var kvp in pack.Metadata.Extra)
        {
            var metaToken = kvp.Value is null ? JValue.CreateNull() : kvp.Value.DeepClone();
            snapshot.Metadata[kvp.Key] = metaToken;
        }

        foreach (var chapter in pack.Chapters)
        {
            if (chapter is null)
            {
                continue;
            }

            var chapterToken = JObject.FromObject(chapter, serializer);
            var path = pack.GetChapterPath(chapter) ?? string.Empty;

            snapshot.Chapters.Add(new PresetChapter
            {
                Path = path,
                Chapter = chapterToken,
            });
        }

        return snapshot;
    }

    private string GetSlotPath(int slot)
    {
        return Path.Combine(presetsDirectory, $"slot{slot}.json");
    }

    private static void ValidateSlot(int slot)
    {
        if (slot < 1 || slot > MaxSlots)
        {
            throw new ArgumentOutOfRangeException(nameof(slot), $"Slot must be between 1 and {MaxSlots}.");
        }
    }

    public sealed record class PresetSlotInfo(int Slot, string? Name, DateTimeOffset? LastModifiedUtc);

    private sealed class PresetData
    {
        public string? Name { get; set; }

        public DateTimeOffset SavedAtUtc { get; set; }

        public PresetSnapshot? Data { get; set; }
    }

    private sealed class PresetSnapshot
    {
        public Dictionary<string, JToken>? Metadata { get; set; }

        public List<string>? MetadataOrder { get; set; }

        public List<PresetChapter>? Chapters { get; set; }
    }

    private sealed class PresetChapter
    {
        public string? Path { get; set; }

        public JObject? Chapter { get; set; }
    }

    private sealed class PresetHeader
    {
        public string? Name { get; set; }

        public DateTimeOffset SavedAtUtc { get; set; }
    }
}




