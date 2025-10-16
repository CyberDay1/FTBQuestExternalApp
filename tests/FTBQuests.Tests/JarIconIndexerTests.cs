using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="JarIconIndexerTests.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System.IO.Compression;
using System.Text.Json;
using FTBQuests.Assets;
using Xunit;

namespace FTBQuests.Tests;

public sealed class JarIconIndexerTests
{
    [Fact]
    public async Task BuildIndexAsync_WritesDeterministicIndex()
    {
        var tempDirectory = Directory.CreateTempSubdirectory();
        try
        {
            var jarsDirectory = Path.Combine(tempDirectory.FullName, "jars");
            Directory.CreateDirectory(jarsDirectory);

            CreateJar(Path.Combine(jarsDirectory, "a-mod.jar"),
            [
                "assets/moda/textures/item/wrench.png",
                "assets/moda/textures/block/bricks.png",
                "assets/moda/textures/ignore/skip.png",
            ]);

            CreateJar(Path.Combine(jarsDirectory, "b-mod.jar"),
            [
                "assets/modb/textures/items/gadget.png",
                "assets/modb/textures/blocks/fancy/block.png",
                "assets/modb/textures/item/wrench.png",
            ]);

            var outputPath = Path.Combine(tempDirectory.FullName, "assets-cache", "icon_index.json");

            var indexer = new JarIconIndexer();
            await indexer.BuildIndexAsync(jarsDirectory, outputPath);

            var json = await File.ReadAllTextAsync(outputPath);
            using var document = JsonDocument.Parse(json);

            var entries = document.RootElement.EnumerateArray().ToList();
            Assert.Equal(5, entries.Count);

            Assert.Collection(entries,
                entry => AssertEntry(entry, "moda:bricks", "assets/moda/textures/block/bricks.png"),
                entry => AssertEntry(entry, "moda:wrench", "assets/moda/textures/item/wrench.png"),
                entry => AssertEntry(entry, "modb:fancy/block", "assets/modb/textures/blocks/fancy/block.png"),
                entry => AssertEntry(entry, "modb:gadget", "assets/modb/textures/items/gadget.png"),
                entry => AssertEntry(entry, "modb:wrench", "assets/modb/textures/item/wrench.png"));
        }
        finally
        {
            tempDirectory.Delete(recursive: true);
        }
    }

    private static void CreateJar(string jarPath, IReadOnlyCollection<string> entries)
    {
        using var stream = File.Create(jarPath);
        using var archive = new ZipArchive(stream, ZipArchiveMode.Create, leaveOpen: false);

        foreach (var entry in entries)
        {
            var zipEntry = archive.CreateEntry(entry, CompressionLevel.NoCompression);
            using var entryStream = zipEntry.Open();
        }
    }

    private static void AssertEntry(JsonElement element, string expectedId, string expectedTexturePath)
    {
        Assert.Equal(expectedId, element.GetProperty("Id").GetString());
        Assert.Equal(expectedTexturePath, element.GetProperty("TexturePath").GetString());
    }
}
