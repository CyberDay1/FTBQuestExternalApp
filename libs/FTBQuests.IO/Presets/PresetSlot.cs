// <copyright file="PresetSlot.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>
using System;

using FTBQuests.Core.Model;

using FTBQuests.Codecs;

using FTBQuests.Assets;

using FTBQuests.Registry;

namespace FTBQuests.IO.Presets;
/// <summary>
/// Represents a single preset slot stored on disk.
/// </summary>
public sealed class PresetSlot
{
    /// <summary>
    /// Initializes a new instance of the <see cref="PresetSlot"/> class.
    /// </summary>
    /// <param name="name">The logical name of the slot.</param>
    /// <param name="filePath">The backing file path.</param>
    public PresetSlot(string name, string filePath)
    {
        ArgumentException.ThrowIfNullOrEmpty(name);
        ArgumentException.ThrowIfNullOrEmpty(filePath);
        Name = name;
        FilePath = filePath;
    }
    /// Gets the logical name of the preset slot.
    public string Name { get; }
    /// Gets the backing file path for the preset slot.
    public string FilePath { get; }
}
