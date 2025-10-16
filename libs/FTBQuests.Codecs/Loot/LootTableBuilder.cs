using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="LootTableBuilder.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using FTBQuests.Codecs.Model;
using Newtonsoft.Json;

namespace FTBQuests.Loot;

/// <summary>
/// Fluent builder responsible for constructing loot tables and exporting them to disk.
/// </summary>
public sealed class LootTableBuilder
{
    private readonly LootTable _lootTable;

    /// <summary>
    /// Initializes a new instance of the <see cref="LootTableBuilder"/> class.
    /// </summary>
    /// <param name="name">The logical loot table name without extension.</param>
    public LootTableBuilder(string name)
    {
        _lootTable = new LootTable(name ?? throw new ArgumentNullException(nameof(name)));
    }

    /// <summary>
    /// Gets the loot table under construction.
    /// </summary>
    public LootTable Table => _lootTable;

    /// <summary>
    /// Adds a loot entry to the table.
    /// </summary>
    public LootTableBuilder AddEntry(Identifier id, int weight, int countMin, int countMax, string? conditions = null)
    {
        if (weight <= 0)
        {
            throw new ArgumentOutOfRangeException(nameof(weight), weight, "Weight must be positive.");
        }

        if (countMin < 0)
        {
            throw new ArgumentOutOfRangeException(nameof(countMin), countMin, "Minimum count must be non-negative.");
        }

        if (countMax < countMin)
        {
            throw new ArgumentException("Maximum count must be greater than or equal to minimum count.", nameof(countMax));
        }

        string? normalizedConditions = string.IsNullOrWhiteSpace(conditions) ? null : conditions;
        _lootTable.Entries.Add(new LootEntry(id, weight, countMin, countMax, normalizedConditions));
        return this;
    }

    /// <summary>
    /// Removes all entries currently associated with the loot table.
    /// </summary>
    public LootTableBuilder ClearEntries()
    {
        _lootTable.Entries.Clear();
        return this;
    }

    /// <summary>
    /// Builds a copy of the current loot table instance.
    /// </summary>
    public LootTable Build()
    {
        var clone = new LootTable(_lootTable.Name);
        foreach (LootEntry entry in _lootTable.Entries)
        {
            clone.Entries.Add(new LootEntry(entry.Id, entry.Weight, entry.CountMin, entry.CountMax, entry.Conditions));
        }

        return clone;
    }

    /// <summary>
    /// Serializes the loot table and writes the JSON representation to disk.
    /// </summary>
    /// <param name="rootDirectory">The root export directory (quest pack root).</param>
    /// <returns>The full file path of the generated loot table JSON.</returns>
    public string Save(string rootDirectory)
    {
        ArgumentException.ThrowIfNullOrEmpty(rootDirectory);

        LootTable table = Build();
        var dto = new LootTableDto
        {
            Name = table.Name,
            Entries = table.Entries
                .Select(entry => new LootEntryDto
                {
                    Id = entry.Id.ToString(),
                    Weight = entry.Weight,
                    CountMin = entry.CountMin,
                    CountMax = entry.CountMax,
                    Conditions = entry.Conditions,
                })
                .ToList(),
        };

        string lootDirectory = Path.Combine(rootDirectory, "data", "ftbquests", "loot_tables");
        Directory.CreateDirectory(lootDirectory);
        string targetPath = Path.Combine(lootDirectory, table.Name + ".json");

        string json = JsonConvert.SerializeObject(dto, Formatting.Indented);
        File.WriteAllText(targetPath, json, Encoding.UTF8);
        return targetPath;
    }

    /// <summary>
    /// Loads a loot table from a JSON file previously produced by the builder.
    /// </summary>
    /// <param name="filePath">The JSON file path.</param>
    /// <returns>The deserialized loot table.</returns>
    public static LootTable Load(string filePath)
    {
        ArgumentException.ThrowIfNullOrEmpty(filePath);
        string json = File.ReadAllText(filePath, Encoding.UTF8);
        var dto = JsonConvert.DeserializeObject<LootTableDto>(json) ?? throw new InvalidDataException("Loot table JSON was empty");

        var table = new LootTable(dto.Name);
        if (dto.Entries is not null)
        {
            foreach (LootEntryDto entry in dto.Entries)
            {
                var identifier = new Identifier(entry.Id);
                table.Entries.Add(new LootEntry(identifier, entry.Weight, entry.CountMin, entry.CountMax, entry.Conditions));
            }
        }

        return table;
    }

    private sealed class LootTableDto
    {
        public string Name { get; set; } = string.Empty;

        public List<LootEntryDto> Entries { get; set; } = new();
    }

    private sealed class LootEntryDto
    {
        public string Id { get; set; } = string.Empty;

        public int Weight { get; set; }

        public int CountMin { get; set; }

        public int CountMax { get; set; }

        public string? Conditions { get; set; }
    }
}

