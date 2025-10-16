using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="PresetManagerDialog.xaml.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using FTBQuestEditor.WinUI.ViewModels;
using FTBQuests.IO;
using Microsoft.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;

namespace FTBQuestEditor.WinUI.Views;

public sealed partial class PresetManagerDialog : ContentDialog
{
    private readonly PresetStore presetStore;
    private readonly Func<FTBQuests.IO.QuestPack?> currentPackAccessor;
    private readonly Action<FTBQuests.IO.QuestPack> loadCallback;

    public PresetManagerDialog(PresetStore presetStore, Func<FTBQuests.IO.QuestPack?> currentPackAccessor, Action<FTBQuests.IO.QuestPack> loadCallback, FrameworkElement rootElement)
    {
        this.presetStore = presetStore ?? throw new ArgumentNullException(nameof(presetStore));
        this.currentPackAccessor = currentPackAccessor ?? throw new ArgumentNullException(nameof(currentPackAccessor));
        this.loadCallback = loadCallback ?? throw new ArgumentNullException(nameof(loadCallback));

        InitializeComponent();
        XamlRoot = rootElement.XamlRoot;
        DataContext = this;
        Loaded += OnLoaded;
    }

    public ObservableCollection<PresetSlotViewModel> Slots { get; } = new(Enumerable.Range(1, PresetStore.MaxSlots).Select(slot => new PresetSlotViewModel(slot)));

    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        await RefreshSlotsAsync();
    }

    private async Task RefreshSlotsAsync()
    {
        var details = await presetStore.ListDetailedAsync();
        var canSave = currentPackAccessor() is not null;

        foreach (var detail in details)
        {
            var viewModel = Slots.FirstOrDefault(vm => vm.Slot == detail.Slot);
            if (viewModel is null)
            {
                continue;
            }

            viewModel.Update(detail.Name, detail.LastModifiedUtc);
            viewModel.CanSave = canSave;
        }

        if (!details.Any(detail => detail.Name is not null))
        {
            SetStatus("No presets saved yet.", isError: false);
        }
        else
        {
            ClearStatus();
        }
    }

    private async void OnSaveClicked(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.DataContext is not PresetSlotViewModel slot)
        {
            return;
        }

        var pack = currentPackAccessor();
        if (pack is null)
        {
            SetStatus("No quest pack is currently loaded to save.", isError: true);
            return;
        }

        var name = slot.EditableName?.Trim();
        if (string.IsNullOrWhiteSpace(name))
        {
            SetStatus("Enter a name before saving the preset.", isError: true);
            return;
        }

        try
        {
            await presetStore.SaveAsync(slot.Slot, name, pack);
            await RefreshSlotsAsync();
            SetStatus($"Saved preset \"{name}\" to slot {slot.Slot}.", isError: false);
        }
        catch (Exception ex)
        {
            SetStatus(ex.Message, isError: true);
        }
    }

    private async void OnLoadClicked(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.DataContext is not PresetSlotViewModel slot)
        {
            return;
        }

        try
        {
            var result = await presetStore.LoadAsync(slot.Slot);
            if (result is null)
            {
                SetStatus("Preset slot is empty.", isError: true);
                return;
            }

            loadCallback(result.Value.Pack);
            Hide();
        }
        catch (Exception ex)
        {
            SetStatus(ex.Message, isError: true);
        }
    }

    private async void OnRenameClicked(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.DataContext is not PresetSlotViewModel slot)
        {
            return;
        }

        var name = slot.EditableName?.Trim();
        if (string.IsNullOrWhiteSpace(name))
        {
            SetStatus("Enter a name before renaming the preset.", isError: true);
            return;
        }

        try
        {
            var result = await presetStore.LoadAsync(slot.Slot);
            if (result is null)
            {
                SetStatus("Preset slot is empty.", isError: true);
                return;
            }

            await presetStore.SaveAsync(slot.Slot, name, result.Value.Pack);
            await RefreshSlotsAsync();
            SetStatus($"Renamed preset in slot {slot.Slot}.", isError: false);
        }
        catch (Exception ex)
        {
            SetStatus(ex.Message, isError: true);
        }
    }

    private async void OnDeleteClicked(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.DataContext is not PresetSlotViewModel slot)
        {
            return;
        }

        try
        {
            await presetStore.DeleteAsync(slot.Slot);
            await RefreshSlotsAsync();
            SetStatus($"Deleted preset from slot {slot.Slot}.", isError: false);
        }
        catch (Exception ex)
        {
            SetStatus(ex.Message, isError: true);
        }
    }

    private void SetStatus(string message, bool isError)
    {
        if (StatusText is null)
        {
            return;
        }

        StatusText.Text = message;
        StatusText.Foreground = isError
            ? new SolidColorBrush(Colors.Red)
            : new SolidColorBrush(Colors.Gray);
    }

    private void ClearStatus()
    {
        if (StatusText is null)
        {
            return;
        }

        StatusText.Text = string.Empty;
    }
}
