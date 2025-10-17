using FTBQuests.Core.Model;
using FTBQuests.Assets;
// <copyright file="RegistryItem.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

namespace FTBQuests.Registry.Model;

/// <summary>
/// Represents an item that can be referenced from quests or rewards.
/// </summary>
public sealed class RegistryItem
{
    /// <summary>
    /// Initializes a new instance of the <see cref="RegistryItem"/> class.
    /// </summary>
    /// <param name="id">The fully qualified identifier of the entry.</param>
    /// <param name="displayName">The default display name.</param>
    /// <param name="optionalNbtTemplate">The optional NBT template captured from the probe.</param>
    /// <param name="sourceModId">The mod identifier that contributed the entry.</param>
    public RegistryItem(string id, string displayName, string? optionalNbtTemplate, string sourceModId)
    {
        ArgumentException.ThrowIfNullOrEmpty(id);
        ArgumentException.ThrowIfNullOrEmpty(displayName);
        ArgumentException.ThrowIfNullOrEmpty(sourceModId);

        Id = id;
        DisplayName = displayName;
        OptionalNbtTemplate = string.IsNullOrWhiteSpace(optionalNbtTemplate) ? null : optionalNbtTemplate;
        SourceModId = sourceModId;
    }

    /// <summary>
    /// Gets the fully qualified identifier.
    /// </summary>
    public string Id { get; }

    /// <summary>
    /// Gets the default display name.
    /// </summary>
    public string DisplayName { get; }

    /// <summary>
    /// Gets the optional NBT template captured from the probe, when present.
    /// </summary>
    public string? OptionalNbtTemplate { get; }

    /// <summary>
    /// Gets the mod identifier that contributed the entry.
    /// </summary>
    public string SourceModId { get; }
}

