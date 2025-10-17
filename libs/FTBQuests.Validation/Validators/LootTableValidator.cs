// <copyright file="LootTableValidator.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>
using System;
using System.Collections.Generic;

using FTBQuests.Assets;

using FTBQuests.Registry;

using FTBQuests.Loot;

namespace FTBQuests.Validation.Validators;

/// <summary>
/// Validates loot table definitions against the registry database.
/// </summary>
public sealed class LootTableValidator
{
    private readonly RegistryDatabase registry;

    public LootTableValidator(RegistryDatabase registry)
    {
        this.registry = registry ?? throw new ArgumentNullException(nameof(registry));
    }

    /// <summary>
    /// Validates the supplied loot table and returns any discovered issues.
    /// </summary>
    /// <param name="table">The table to inspect.</param>
    /// <returns>A list of validation error descriptions.</returns>
    public IReadOnlyList<string> Validate(LootTable table)
    {
        ArgumentNullException.ThrowIfNull(table);

        var issues = new List<string>();
        foreach (LootEntry entry in table.Entries)
        {
            if (entry.Weight <= 0)
            {
                issues.Add($"Entry '{entry.Id}' must have a positive weight.");
            }

            if (!registry.TryGetByIdentifier(entry.Id, out _))
            {
                issues.Add($"Entry '{entry.Id}' references an item that does not exist in the registry.");
            }
        }

        return issues;
    }
}
