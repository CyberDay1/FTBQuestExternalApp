namespace FTBQuests.Loot;

using System;
using System.Collections.Generic;

/// <summary>
/// Represents a named group of loot tables.
/// </summary>
public sealed class LootTableGroup
{
    /// <summary>
    /// Initializes a new instance of the <see cref="LootTableGroup"/> class.
    /// </summary>
    /// <param name="name">The group name.</param>
    public LootTableGroup(string name)
    {
        ArgumentException.ThrowIfNullOrEmpty(name);
        Name = name;
    }

    /// <summary>
    /// Gets the unique group name.
    /// </summary>
    public string Name { get; }

    /// <summary>
    /// Gets the collection of loot table identifiers that make up the group.
    /// </summary>
    public List<string> TableNames { get; } = new();
}
