using FTBQuests.Core.Validation;
using FTBQuests.Codecs;
using FTBQuests.Core.Model;

using FTBQuests.Codecs;
using FTBQuests.Core.Model;




using FTBQuests.Assets;
// <copyright file="PresetSlotStore.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;

namespace FTBQuests.IO.Presets;

/// <summary>
/// Provides enumeration and deletion helpers for preset slots stored on disk.
/// </summary>
public sealed class PresetSlotStore
{
    private readonly string slotsDirectory;

    /// <summary>
    /// Initializes a new instance of the <see cref="PresetSlotStore"/> class.
    /// </summary>
    /// <param name="slotsDirectory">The directory that contains preset slot files.</param>
    public PresetSlotStore(string slotsDirectory)
    {
        ArgumentException.ThrowIfNullOrEmpty(slotsDirectory);

        this.slotsDirectory = slotsDirectory;
        Directory.CreateDirectory(slotsDirectory);
    }

    /// <summary>
    /// Gets the directory containing the preset slots.
    /// </summary>
    public string SlotsDirectory => slotsDirectory;

    /// <summary>
    /// Enumerates the preset slots that exist on disk.
    /// </summary>
    /// <returns>A collection of preset slots sorted by name.</returns>
    public IReadOnlyList<PresetSlot> GetSlots()
    {
        if (!Directory.Exists(slotsDirectory))
        {
            return Array.Empty<PresetSlot>();
        }

        return Directory.EnumerateFiles(slotsDirectory)
            .OrderBy(static file => Path.GetFileNameWithoutExtension(file), StringComparer.OrdinalIgnoreCase)
            .Select(CreateSlot)
            .ToList();
    }

    /// <summary>
    /// Deletes the slot with the provided name.
    /// </summary>
    /// <param name="slotName">The logical slot name.</param>
    /// <returns><see langword="true"/> when a slot was removed.</returns>
    public bool DeleteSlot(string slotName)
    {
        ArgumentException.ThrowIfNullOrEmpty(slotName);

        string? path = ResolveSlotPath(slotName);
        if (path is null)
        {
            return false;
        }

        if (File.Exists(path))
        {
            File.Delete(path);
        }

        return true;
    }

    private PresetSlot CreateSlot(string filePath)
    {
        string name = Path.GetFileNameWithoutExtension(filePath);
        return new PresetSlot(name, filePath);
    }

    private string? ResolveSlotPath(string slotName)
    {
        if (!Directory.Exists(slotsDirectory))
        {
            return null;
        }

        return Directory.EnumerateFiles(slotsDirectory)
            .FirstOrDefault(file => string.Equals(Path.GetFileNameWithoutExtension(file), slotName, StringComparison.OrdinalIgnoreCase));
    }
}



