using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;

namespace FTBQuestEditor.WinUI.ViewModels;

public sealed class PresetSlotViewModel : ObservableObject
{
    private string? name;
    private DateTimeOffset? lastModified;
    private string editableName = string.Empty;
    private bool canSave;

    public PresetSlotViewModel(int slot)
    {
        Slot = slot;
    }

    public int Slot { get; }

    public string SlotLabel => $"Slot {Slot}";

    public string? Name
    {
        get => name;
        private set => SetProperty(ref name, value);
    }

    public DateTimeOffset? LastModified
    {
        get => lastModified;
        private set => SetProperty(ref lastModified, value);
    }

    public string EditableName
    {
        get => editableName;
        set => SetProperty(ref editableName, value);
    }

    public bool CanSave
    {
        get => canSave;
        set => SetProperty(ref canSave, value);
    }

    public bool HasPreset => !string.IsNullOrWhiteSpace(Name);

    public bool CanLoad => HasPreset;

    public bool CanRename => HasPreset;

    public bool CanDelete => HasPreset;

    public string DisplayName => string.IsNullOrWhiteSpace(Name) ? "Empty" : Name!;

    public string LastModifiedDisplay => LastModified.HasValue
        ? LastModified.Value.ToLocalTime().ToString("g", CultureInfo.CurrentCulture)
        : "Never";

    public void Update(string? presetName, DateTimeOffset? lastModifiedUtc)
    {
        Name = presetName;
        LastModified = lastModifiedUtc;
        EditableName = presetName ?? string.Empty;

        OnPropertyChanged(nameof(DisplayName));
        OnPropertyChanged(nameof(HasPreset));
        OnPropertyChanged(nameof(CanLoad));
        OnPropertyChanged(nameof(CanRename));
        OnPropertyChanged(nameof(CanDelete));
        OnPropertyChanged(nameof(LastModifiedDisplay));
    }
}
