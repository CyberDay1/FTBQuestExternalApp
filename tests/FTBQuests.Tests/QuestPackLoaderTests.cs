using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="QuestPackLoaderTests.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using FTBQuestExternalApp.Codecs.Model;
using FTBQuests.IO;
using FTBQuests.Codecs;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using Xunit;

namespace FTBQuests.Tests;

public static class QuestPackLoaderTests
{
    [Fact]
    public static async Task LoadAsync_ReadsSamplePack()
    {
        var fixtureRoot = GetFixturePath("SamplePack");
        var loader = new QuestPackLoader();

        var pack = await loader.LoadAsync(fixtureRoot);

        Assert.Equal(2, pack.Chapters.Count);
        Assert.Equal("Getting Started", pack.Chapters[0].Title);
        Assert.Equal("Automation", pack.Chapters[1].Title);

        Assert.True(pack.Metadata.TryGetValue("info.json", out var infoToken));
        Assert.Equal(12, infoToken?["pack_format"]?.Value<int>());

        Assert.True(pack.Metadata.TryGetValue("teams/teams.json", out var teamsToken));
        var teamName = teamsToken?["teams"]?.FirstOrDefault()?["display_name"]?.Value<string>();
        Assert.Equal("Builders", teamName);

        Assert.True(pack.Chapters[0].Extra.TryGetValue("chapter_color", out var colorToken));
        Assert.Equal("#ffcc00", colorToken?.Value<string>());

        Assert.Single(pack.Chapters[0].Quests);
        Assert.True(pack.Chapters[0].Quests[0].Extra.TryGetValue("custom_property", out var questExtra));
        Assert.True(questExtra?.Value<bool>());
    }

    [Fact]
    public static async Task SaveAsync_RoundTripsWithoutChanges()
    {
        var fixtureRoot = GetFixturePath("SamplePack");
        var loader = new QuestPackLoader();
        var pack = await loader.LoadAsync(fixtureRoot);

        var tempRoot = Path.Combine(Path.GetTempPath(), "QuestPackRoundTrip", Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(tempRoot);

        try
        {
            await loader.SaveAsync(pack, tempRoot);

            var expectedDir = Path.Combine(fixtureRoot, "data", "ftbquests");
            var actualDir = Path.Combine(tempRoot, "data", "ftbquests");

            var expectedFiles = ReadJsonFiles(expectedDir);
            var actualFiles = ReadJsonFiles(actualDir);

            Assert.Equal(expectedFiles.Keys.OrderBy(f => f), actualFiles.Keys.OrderBy(f => f));

            foreach (var key in expectedFiles.Keys)
            {
                Assert.True(actualFiles.TryGetValue(key, out var actualContent));
                Assert.Equal(expectedFiles[key], actualContent);
            }
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
    public static async Task LoadEditSaveReload_PreservesSemantics()
    {
        var fixtureRoot = GetFixturePath("RoundTripPack");
        var loader = new QuestPackLoader();
        var serializer = JsonSerializer.Create(JsonSettings.Create());

        var pack = await loader.LoadAsync(fixtureRoot);

        Assert.True(pack.Metadata.TryGetValue("info.json", out var infoToken));
        var info = Assert.IsType<JObject>(infoToken);
        info["revision"] = 2;
        var extra = Assert.IsType<JObject>(info["extra"]);
        extra["difficulty"] = "master";
        extra["authors"] = new JArray("BuilderBot", "AutomationAI", "TestRunner");

        Assert.True(pack.Metadata.TryGetValue("teams/teams.json", out var teamsToken));
        var teams = Assert.IsType<JObject>(teamsToken);
        var teamArray = Assert.IsType<JArray>(teams["teams"]);
        var engineers = Assert.IsType<JObject>(teamArray[0]);
        engineers["display_name"] = "Chief Engineers";
        engineers["color"] = "#5a9cfe";

        var gatherQuest = FindQuest(pack, 2);
        gatherQuest.PositionX = -4;
        gatherQuest.PositionY = 3;

        var craftQuest = FindQuest(pack, 3);
        craftQuest.PositionX = 12;
        craftQuest.PositionY = -2;
        craftQuest.Page = 1;
        craftQuest.ClearDependencies();
        craftQuest.AddDependency(gatherQuest.Id);

        var furnaceQuest = FindQuest(pack, 5);
        furnaceQuest.PositionX = 18;
        furnaceQuest.PositionY = 9;
        furnaceQuest.Page = 2;
        furnaceQuest.ClearDependencies();
        furnaceQuest.AddDependency(gatherQuest.Id);
        furnaceQuest.AddDependency(craftQuest.Id);

        var expectedSnapshot = CreateSnapshot(pack, serializer);

        var tempRoot = Path.Combine(Path.GetTempPath(), "QuestPackRoundTrip", Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(tempRoot);

        try
        {
            await loader.SaveAsync(pack, tempRoot);

            var reloaded = await loader.LoadAsync(tempRoot);
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

    private static string GetFixturePath(string name)
    {
        var baseDirectory = AppContext.BaseDirectory;
        return Path.GetFullPath(Path.Combine(baseDirectory, "Fixtures", name));
    }

    private static Dictionary<string, string> ReadJsonFiles(string directory)
    {
        if (!Directory.Exists(directory))
        {
            return new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        }

        var files = Directory.EnumerateFiles(directory, "*.json", SearchOption.AllDirectories);
        return files.ToDictionary(
            file => Normalize(file, directory),
            file => File.ReadAllText(file),
            StringComparer.OrdinalIgnoreCase);
    }

    private static string Normalize(string fullPath, string root)
    {
        var relative = Path.GetRelativePath(root, fullPath);
        var normalized = relative.Replace(Path.DirectorySeparatorChar, '/');
        return normalized.Replace(Path.AltDirectorySeparatorChar, '/');
    }

    private static Quest FindQuest(FTBQuests.IO.QuestPack pack, long questId)
    {
        return pack.Chapters
            .SelectMany(chapter => chapter.Quests)
            .First(quest => quest.Id == questId);
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

    private sealed record QuestPackSnapshot(
        IReadOnlyDictionary<string, JToken> Metadata,
        IReadOnlyList<JToken> Chapters);
}
