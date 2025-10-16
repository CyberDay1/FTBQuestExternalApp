using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="LootTableGroupValidator.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.Generic;
using System.Linq;
using FTBQuests.Loot;

namespace FTBQuests.Validation.Validators;

/// <summary>
/// Validates that loot table groups refer to known tables.
/// </summary>
public sealed class LootTableGroupValidator
{
    /// <summary>
    /// Validates that all table references inside <paramref name="group"/> resolve to
    /// the provided loot tables.
    /// </summary>
    /// <param name="group">The group to validate.</param>
    /// <param name="knownTables">The set of known tables.</param>
    /// <returns>A collection of validation messages.</returns>
    public IReadOnlyList<string> Validate(LootTableGroup group, IReadOnlyCollection<LootTable> knownTables)
    {
        ArgumentNullException.ThrowIfNull(group);
        ArgumentNullException.ThrowIfNull(knownTables);

        var messages = new List<string>();
        if (group.TableNames.Count == 0)
        {
            messages.Add($"Group '{group.Name}' does not contain any loot tables.");
            return messages;
        }

        var knownNames = new HashSet<string>(knownTables.Select(table => table.Name), StringComparer.OrdinalIgnoreCase);
        foreach (string tableName in group.TableNames)
        {
            if (!knownNames.Contains(tableName))
            {
                messages.Add($"Group '{group.Name}' references missing loot table '{tableName}'.");
            }
        }

        return messages;
    }
}
