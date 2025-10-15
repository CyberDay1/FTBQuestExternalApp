using System;

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

    /// <summary>
    /// Gets the logical name of the preset slot.
    /// </summary>
    public string Name { get; }

    /// <summary>
    /// Gets the backing file path for the preset slot.
    /// </summary>
    public string FilePath { get; }
}
