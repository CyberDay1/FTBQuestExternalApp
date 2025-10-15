// <copyright file="ImportPathResolutionTests.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.IO;
using System.Threading.Tasks;
using FTBQuests.IO;
using Newtonsoft.Json.Linq;
using Xunit;

namespace FTBQuests.Tests;

public class ImportPathResolutionTests
{
    [Fact]
    public async Task LoadAsync_PrefersConfigWhenBothExist()
    {
        string root = CreateTempDirectory();
        try
        {
            string configRoot = Path.Combine(root, "config", "ftbquests");
            string dataRoot = Path.Combine(root, "data", "ftbquests");
            Directory.CreateDirectory(configRoot);
            Directory.CreateDirectory(dataRoot);

            await File.WriteAllTextAsync(Path.Combine(configRoot, "info.json"), "{\"pack_format\":42}");
            await File.WriteAllTextAsync(Path.Combine(dataRoot, "info.json"), "{\"pack_format\":7}");

            var loader = new QuestPackLoader();
            var pack = await loader.LoadAsync(root);

            Assert.True(pack.Metadata.TryGetValue("info.json", out JToken? infoToken));
            Assert.Equal(42, infoToken?["pack_format"]?.Value<int>());
        }
        finally
        {
            Directory.Delete(root, recursive: true);
        }
    }

    [Fact]
    public async Task LoadAsync_FallsBackToDataWhenConfigMissing()
    {
        string root = CreateTempDirectory();
        try
        {
            string dataRoot = Path.Combine(root, "data", "ftbquests");
            Directory.CreateDirectory(dataRoot);

            await File.WriteAllTextAsync(Path.Combine(dataRoot, "info.json"), "{\"pack_format\":99}");

            var loader = new QuestPackLoader();
            var pack = await loader.LoadAsync(root);

            Assert.True(pack.Metadata.TryGetValue("info.json", out JToken? infoToken));
            Assert.Equal(99, infoToken?["pack_format"]?.Value<int>());
        }
        finally
        {
            Directory.Delete(root, recursive: true);
        }
    }

    [Fact]
    public async Task LoadAsync_ThrowsWhenQuestDirectoryMissing()
    {
        string root = CreateTempDirectory();
        try
        {
            var loader = new QuestPackLoader();
            var exception = await Assert.ThrowsAsync<DirectoryNotFoundException>(() => loader.LoadAsync(root));

            Assert.Contains("config", exception.Message, StringComparison.OrdinalIgnoreCase);
            Assert.Contains("data", exception.Message, StringComparison.OrdinalIgnoreCase);
        }
        finally
        {
            Directory.Delete(root, recursive: true);
        }
    }

    private static string CreateTempDirectory()
    {
        string path = Path.Combine(Path.GetTempPath(), "ftbq_import_" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(path);
        return path;
    }
}
