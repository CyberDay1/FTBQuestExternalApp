using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="QuestPackExportTests.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Threading.Tasks;
using FTBQuestExternalApp.Codecs.Model;
using FTBQuests.Codecs;
using FTBQuests.IO;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using Xunit;

namespace FTBQuests.Tests;

public static class QuestPackExportTests
{
    [Fact]
    public static async Task ExportAsync_WritesExpectedStructure()
    {
        var fixtureRoot = GetFixturePath("RoundTripPack");
        var loader = new QuestPackLoader();
        var exporter = new QuestPackExporter();
        var pack = await loader.LoadAsync(fixtureRoot);

        var tempRoot = Path.Combine(Path.GetTempPath(), "QuestPackExportTests", Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(tempRoot);

        try
        {
            var zipPath = Path.Combine(tempRoot, "ftbquests.zip");
            await exporter.ExportAsync(pack, zipPath);

            Assert.True(File.Exists(zipPath));

            using var archive = ZipFile.OpenRead(zipPath);
            var entries = archive.Entries.Select(entry => entry.FullName).OrderBy(name => name, StringComparer.Ordinal).ToList();

            Assert.Contains("metadata.json", entries);
            Assert.Contains("config/ftbquests/info.json", entries);
            Assert.Contains("config/ftbquests/chapters/foundations.json", entries);
            Assert.Contains("config/ftbquests/chapters/industry.json", entries);

            var metadataEntry = archive.GetEntry("metadata.json");
            Assert.NotNull(metadataEntry);

            using var metadataStream = metadataEntry!.Open();
            using var reader = new StreamReader(metadataStream);
            var metadataJson = await reader.ReadToEndAsync();
            var metadata = JObject.Parse(metadataJson);

            Assert.True(metadata.TryGetValue("toolVersion", out var toolVersion));
            Assert.False(string.IsNullOrWhiteSpace(toolVersion?.Value<string>()));

            Assert.True(metadata.TryGetValue("exportedAt", out var exportedAtToken));
            Assert.True(DateTimeOffset.TryParse(exportedAtToken?.Value<string>(), out _));
        }
        finally
        {
            if (Directory.Exists(tempRoot))
            {
                Directory.Delete(tempRoot, recursive: true);
            }
        }
    }

    [Fact]
    public static async Task ExportAsync_RoundTripsQuestPack()
    {
        var fixtureRoot = GetFixturePath("RoundTripPack");
        var loader = new QuestPackLoader();
        var exporter = new QuestPackExporter();
        var serializer = JsonSerializer.Create(JsonSettings.Create());

        var pack = await loader.LoadAsync(fixtureRoot);
        var expectedSnapshot = CreateSnapshot(pack, serializer);

        var tempRoot = Path.Combine(Path.GetTempPath(), "QuestPackExportTests", Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(tempRoot);

        try
        {
            var zipPath = Path.Combine(tempRoot, "ftbquests.zip");
            await exporter.ExportAsync(pack, zipPath);

            var extractRoot = Path.Combine(tempRoot, "extracted");
            ZipFile.ExtractToDirectory(zipPath, extractRoot);

            var reloaded = await loader.LoadAsync(extractRoot);
            var actualSnapshot = CreateSnapshot(reloaded, serializer);

            AssertSnapshotsEqual(expectedSnapshot, actualSnapshot);
        }
        finally
        {
            if (Directory.Exists(tempRoot))
            {
                Directory.Delete(tempRoot, recursive: true);
            }
        }
    }

    private static QuestPackSnapshot CreateSnapshot(FTBQuests.IO.QuestPack pack, JsonSerializer serializer)
    {
        var metadata = new Dictionary<string, JToken>(StringComparer.Ordinal);

        foreach (var kvp in pack.Metadata.Extra)
        {
            metadata[kvp.Key] = kvp.Value.DeepClone();
        }

        var chapters = pack.Chapters
            .Select(chapter => JToken.FromObject(chapter, serializer))
            .Select(token => token.DeepClone())
            .ToList();

        return new QuestPackSnapshot(
            new ReadOnlyDictionary<string, JToken>(metadata),
            new ReadOnlyCollection<JToken>(chapters));
    }

    private static void AssertSnapshotsEqual(QuestPackSnapshot expected, QuestPackSnapshot actual)
    {
        Assert.Equal(
            expected.Metadata.Keys.OrderBy(k => k, StringComparer.Ordinal),
            actual.Metadata.Keys.OrderBy(k => k, StringComparer.Ordinal));

        foreach (var key in expected.Metadata.Keys)
        {
            Assert.True(actual.Metadata.TryGetValue(key, out var actualToken));
            Assert.True(JToken.DeepEquals(expected.Metadata[key], actualToken),
                $"Metadata mismatch for '{key}'.\nExpected: {expected.Metadata[key]}\nActual: {actualToken}");
        }

        Assert.Equal(expected.Chapters.Count, actual.Chapters.Count);

        for (var index = 0; index < expected.Chapters.Count; index++)
        {
            var expectedChapter = expected.Chapters[index];
            var actualChapter = actual.Chapters[index];

            Assert.True(JToken.DeepEquals(expectedChapter, actualChapter),
                $"Chapter at index {index} did not round-trip.\nExpected: {expectedChapter}\nActual: {actualChapter}");
        }
    }

    private static string GetFixturePath(string name)
    {
        var baseDirectory = AppContext.BaseDirectory;
        return Path.GetFullPath(Path.Combine(baseDirectory, "Fixtures", name));
    }

    private sealed record QuestPackSnapshot(
        IReadOnlyDictionary<string, JToken> Metadata,
        IReadOnlyList<JToken> Chapters);
}
