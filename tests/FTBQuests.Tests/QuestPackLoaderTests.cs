using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using FTBQuests.IO;
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
}
