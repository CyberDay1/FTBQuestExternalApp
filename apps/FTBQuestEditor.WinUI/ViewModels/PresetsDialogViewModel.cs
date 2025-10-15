using System;
using System.Collections.ObjectModel;
using FTBQuests.IO.Presets;

namespace FTBQuestEditor.WinUI.ViewModels;

/// <summary>
/// View model that backs the presets dialog.
/// </summary>
public sealed class PresetsDialogViewModel
{
    private readonly PresetSlotStore store;

    /// <summary>
    /// Initializes a new instance of the <see cref="PresetsDialogViewModel"/> class.
    /// </summary>
    /// <param name="store">The preset slot store.</param>
    public PresetsDialogViewModel(PresetSlotStore store)
    {
        this.store = store ?? throw new ArgumentNullException(nameof(store));
        Slots = new ObservableCollection<PresetSlotViewModel>();
    }

    /// <summary>
    /// Gets the collection of available slots.
    /// </summary>
    public ObservableCollection<PresetSlotViewModel> Slots { get; }

    /// <summary>
    /// Reloads the preset slots from disk.
    /// </summary>
    public void LoadSlots()
    {
        Slots.Clear();
        foreach (PresetSlot slot in store.GetSlots())
        {
            Slots.Add(new PresetSlotViewModel(slot.Name, slot.FilePath));
        }
    }

    /// <summary>
    /// Deletes the specified slot.
    /// </summary>
    /// <param name="slot">The slot to remove.</param>
    /// <returns><see langword="true"/> when removal succeeded.</returns>
    public bool DeleteSlot(PresetSlotViewModel slot)
    {
        ArgumentNullException.ThrowIfNull(slot);

        bool deleted = store.DeleteSlot(slot.Name);
        if (deleted)
        {
            Slots.Remove(slot);
        }

        return deleted;
    }

    /// <summary>
    /// Represents a single preset slot entry in the dialog.
    /// </summary>
    public sealed class PresetSlotViewModel
    {
        /// <summary>
        /// Initializes a new instance of the <see cref="PresetSlotViewModel"/> class.
        /// </summary>
        /// <param name="name">The slot name.</param>
        /// <param name="filePath">The backing file path.</param>
        public PresetSlotViewModel(string name, string filePath)
        {
            Name = name;
            FilePath = filePath;
        }

        /// <summary>
        /// Gets the logical name for the slot.
        /// </summary>
        public string Name { get; }

        /// <summary>
        /// Gets the file path for the slot.
        /// </summary>
        public string FilePath { get; }
    }
}
