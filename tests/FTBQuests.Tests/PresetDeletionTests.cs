using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="PresetDeletionTests.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.IO;
using FTBQuests.IO.Presets;
using Xunit;

namespace FTBQuests.Tests;

public sealed class PresetDeletionTests
{
    [Fact]
    public void DeleteSlot_RemovesBackingFile()
    {
        string root = CreateTempDirectory();

        try
        {
            string builder = Path.Combine(root, "builder.json");
            string explorer = Path.Combine(root, "explorer.json");
            File.WriteAllText(builder, "{}");
            File.WriteAllText(explorer, "{}");

            var store = new PresetSlotStore(root);
            Assert.Equal(2, store.GetSlots().Count);

            bool deleted = store.DeleteSlot("builder");

            Assert.True(deleted);
            Assert.False(File.Exists(builder));
            var remaining = store.GetSlots();
            Assert.Single(remaining);
            Assert.Equal("explorer", remaining[0].Name);
        }
        finally
        {
            Directory.Delete(root, recursive: true);
        }
    }

    [Fact]
    public void DeleteSlot_IgnoresMissingEntries()
    {
        string root = CreateTempDirectory();

        try
        {
            var store = new PresetSlotStore(root);
            Assert.False(store.DeleteSlot("missing"));
        }
        finally
        {
            Directory.Delete(root, recursive: true);
        }
    }

    private static string CreateTempDirectory()
    {
        string path = Path.Combine(Path.GetTempPath(), "ftbq_presets_" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(path);
        return path;
    }
}
