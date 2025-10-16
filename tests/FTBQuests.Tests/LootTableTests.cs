using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="LootTableTests.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using FTBQuestEditor.WinUI.ViewModels;
using FTBQuestEditor.WinUI.ViewModels.Loot;
using FTBQuestExternalApp.Codecs.Model;
using FTBQuests.Loot;
using FTBQuests.Registry;
using FTBQuests.Registry.Model;
using FTBQuests.Validation.Validators;
using Xunit;

namespace FTBQuests.Tests;

public class LootTableTests
{
    [Fact]
    public void LootTable_RoundTripsThroughJson()
    {
        string root = CreateTempDirectory();
        try
        {
            var builder = new LootTableBuilder("example_table");
            builder
                .AddEntry(new Identifier("minecraft:apple"), 2, 1, 3)
                .AddEntry(new Identifier("minecraft:iron_ingot"), 5, 1, 1, "{\"nbt\":true}");

            string path = builder.Save(root);
            LootTable loaded = LootTableBuilder.Load(path);

            Assert.Equal("example_table", loaded.Name);
            Assert.Equal(2, loaded.Entries.Count);

            Assert.Contains(loaded.Entries, entry => entry.Id == new Identifier("minecraft:apple") && entry.Weight == 2 && entry.CountMin == 1 && entry.CountMax == 3);
            Assert.Contains(loaded.Entries, entry => entry.Id == new Identifier("minecraft:iron_ingot") && entry.Weight == 5 && entry.CountMin == 1 && entry.CountMax == 1 && entry.Conditions == "{\"nbt\":true}");

            var roundTripBuilder = new LootTableBuilder(loaded.Name);
            foreach (LootEntry entry in loaded.Entries)
            {
                roundTripBuilder.AddEntry(entry.Id, entry.Weight, entry.CountMin, entry.CountMax, entry.Conditions);
            }

            string roundTripPath = roundTripBuilder.Save(root);
            LootTable roundTripped = LootTableBuilder.Load(roundTripPath);

            Assert.Equal(loaded.Name, roundTripped.Name);
            Assert.Equal(loaded.Entries.Count, roundTripped.Entries.Count);
            foreach (LootEntry entry in loaded.Entries)
            {
                Assert.Contains(roundTripped.Entries, candidate => candidate.Id == entry.Id && candidate.Weight == entry.Weight && candidate.CountMin == entry.CountMin && candidate.CountMax == entry.CountMax && candidate.Conditions == entry.Conditions);
            }
        }
        finally
        {
            Directory.Delete(root, recursive: true);
        }
    }

    [Fact]
    public void LootTableValidator_FlagsMissingItemsAndBadWeights()
    {
        var items = new[]
        {
            new RegistryItem("minecraft:apple", "Apple", null, "minecraft"),
        };
        var registry = new RegistryDatabase(items, new Dictionary<string, IReadOnlyCollection<string>>());
        var validator = new LootTableValidator(registry);
        var table = new LootTable("validation");
        table.Entries.Add(new LootEntry(new Identifier("minecraft:apple"), 1, 1, 1));
        table.Entries.Add(new LootEntry(new Identifier("minecraft:missing"), 1, 1, 1));
        table.Entries.Add(new LootEntry(new Identifier("minecraft:bad_weight"), 0, 1, 1));

        IReadOnlyList<string> issues = validator.Validate(table);
        Assert.Contains(issues, issue => issue.Contains("minecraft:missing", StringComparison.Ordinal));
        Assert.Contains(issues, issue => issue.Contains("minecraft:bad_weight", StringComparison.Ordinal));
        Assert.DoesNotContain(issues, issue => issue.Contains("minecraft:apple", StringComparison.Ordinal));
    }

    [Fact]
    public void LootTableEditorViewModel_FiltersItemsByMod()
    {
        var items = new[]
        {
            new RegistryItem("minecraft:apple", "Apple", null, "minecraft"),
            new RegistryItem("modded:gear", "Gear", null, "modded"),
            new RegistryItem("modded:core", "Core", null, "modded"),
        };
        var registry = new RegistryDatabase(items, new Dictionary<string, IReadOnlyCollection<string>>());
        var validator = new LootTableValidator(registry);
        var viewModel = new LootTableEditorViewModel(registry, validator);

        Assert.Equal(LootTableEditorViewModel.AllModsFilter, viewModel.SelectedModId);
        Assert.Equal(3, viewModel.AvailableItems.Count);

        viewModel.SelectedModId = "modded";
        Assert.All(viewModel.AvailableItems, item => Assert.Equal("modded", item.ModId));
        Assert.Equal(2, viewModel.AvailableItems.Count);

        viewModel.SelectedModId = "minecraft";
        Assert.Single(viewModel.AvailableItems);
        Assert.Equal("minecraft", viewModel.AvailableItems.Single().ModId);
    }

    [Fact]
    public void LootTableGroupBuilder_RoundTripsThroughJson()
    {
        string root = CreateTempDirectory();
        try
        {
            var builder = new LootTableGroupBuilder("boss_rewards");
            builder.AddTable("boss_table").AddTable("rare_loot");

            string path = builder.Save(root);
            Assert.True(File.Exists(path));

            LootTableGroup loaded = LootTableGroupBuilder.Load(path);
            Assert.Equal("boss_rewards", loaded.Name);
            Assert.Equal(2, loaded.TableNames.Count);
            Assert.Contains("boss_table", loaded.TableNames);
            Assert.Contains("rare_loot", loaded.TableNames);
        }
        finally
        {
            Directory.Delete(root, recursive: true);
        }
    }

    [Fact]
    public void LootTableGroupValidator_FindsMissingTables()
    {
        var group = new LootTableGroup("boss_rewards");
        group.TableNames.Add("existing");
        group.TableNames.Add("missing");

        var tables = new[] { new LootTable("existing") };
        var validator = new LootTableGroupValidator();

        IReadOnlyList<string> issues = validator.Validate(group, tables);
        Assert.Single(issues);
        Assert.Contains("missing", issues.Single(), StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void LootTableManagerViewModel_AddsRenamesAndDeletesGroups()
    {
        var items = new[]
        {
            new RegistryItem("minecraft:apple", "Apple", null, "minecraft"),
        };

        var registry = new RegistryDatabase(items, new Dictionary<string, IReadOnlyCollection<string>>());
        var tableValidator = new LootTableValidator(registry);
        var groupValidator = new LootTableGroupValidator();
        var manager = new LootTableManagerViewModel(registry, tableValidator, groupValidator);
        manager.Load(Array.Empty<LootTable>(), Array.Empty<LootTableGroup>());

        LootTableManagerViewModel.GroupViewModel group = manager.AddGroup("group");
        Assert.Equal(group, manager.SelectedGroup);
        Assert.Single(manager.Groups);

        LootTableManagerViewModel.LootTableItemViewModel table = manager.AddTable("table");
        Assert.Equal(table, manager.SelectedTable);
        Assert.Equal(group, table.Group);
        Assert.Single(manager.VisibleTables);

        // Apply editor changes with no entries to ensure syncing succeeds.
        manager.SyncEditorToSelectedTable();

        manager.RenameGroup(group, "renamed_group");
        Assert.Equal("renamed_group", group.Name);

        manager.AssignTableToGroup(table, null);
        Assert.Null(table.Group);
        Assert.Empty(group.Tables);
        Assert.Empty(manager.VisibleTables);

        manager.SelectedGroup = group;
        manager.AssignTableToGroup(table, group);
        Assert.Single(manager.VisibleTables);

        IReadOnlyList<LootTableGroup> builtGroups = manager.BuildGroups();
        Assert.Single(builtGroups);
        Assert.Contains("table", builtGroups[0].TableNames);

        manager.DeleteGroup(group);
        Assert.Empty(manager.Groups);
    }

    private static string CreateTempDirectory()
    {
        string path = Path.Combine(Path.GetTempPath(), "ftbq_loot_" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(path);
        return path;
    }
}
