using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="MainWindow.xaml.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.IO;
using System.Collections.Generic;
using System.Threading.Tasks;
using FTBQuestEditor.WinUI.Views;
using FTBQuests.IO;
using FTBQuests.IO.Presets;
using FTBQuestEditor.WinUI.ViewModels;
using FTBQuestEditor.WinUI.Views;
using FTBQuests.Loot;
using FTBQuests.Registry;
using FTBQuests.Validation.Validators;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Windows.Storage.Pickers;
using Windows.Storage;
using Windows.Storage.Provider;
using WinRT.Interop;
using LootViews = FTBQuestEditor.WinUI.Views.Loot;
using LootViewModels = FTBQuestEditor.WinUI.ViewModels.Loot;

namespace FTBQuestEditor.WinUI;

public sealed partial class MainWindow : Window
{
    private readonly QuestPackLoader loader = new();
    private Task<RegistryDatabase>? registryTask;
    private LootViewModels.LootTableManagerViewModel? lootManager;
    private readonly QuestPackExporter exporter = new();
    private FTBQuests.IO.QuestPack? currentPack;

    public MainWindow()
    {
        InitializeComponent();
        ViewModel = QuestGraphViewModel.CreateSample();
        DataContext = ViewModel;
    }

    public QuestGraphViewModel ViewModel { get; }
    private async void OnImportClicked(object sender, RoutedEventArgs e)
    {
        var picker = new FolderPicker();
        picker.FileTypeFilter.Add("*");
        picker.SuggestedStartLocation = PickerLocationId.ComputerFolder;

        var hwnd = WindowNative.GetWindowHandle(this);
        InitializeWithWindow.Initialize(picker, hwnd);

        var folder = await picker.PickSingleFolderAsync();
        if (folder is null)
        {
            return;
        }

        try
        {
            var pack = await loader.LoadAsync(folder.Path);
            Navigator?.LoadQuestPack(pack);
            currentPack = pack;
        }
        catch (Exception ex)
        {
            await ShowErrorAsync("Import failed", ex.Message);
        }
    }

    private async void OnPresetsClicked(object sender, RoutedEventArgs e)
    {
        if (Content is not FrameworkElement rootElement)
        {
            return;
        }

        string presetsDirectory = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            "FTBQuestEditor",
            "presets");

        var store = new PresetSlotStore(presetsDirectory);
        var dialog = new PresetsDialog
        {
            XamlRoot = rootElement.XamlRoot,
            ViewModel = new PresetsDialogViewModel(store),
        };

        await dialog.ShowAsync();
    private async void OnLootTablesClicked(object sender, RoutedEventArgs e)
    {
        try
        {
            LootViewModels.LootTableManagerViewModel manager = await EnsureLootManagerAsync();
            var page = new LootViews.LootTableManager
            {
                ViewModel = manager,
            };

            var window = new Window
            {
                Content = page,
            };

            window.Activate();
        }
        catch (Exception ex)
        {
            await ShowErrorAsync("Loot table manager error", ex.Message);
        }
    }

    private async Task ShowErrorAsync(string title, string message)
    private async void OnSaveClicked(object sender, RoutedEventArgs e)
    {
        if (currentPack is null)
        {
            ShowToast(
                "Nothing to export",
                "Import a quest pack before saving.",
                InfoBarSeverity.Warning);
            return;
        }

        var picker = new FileSavePicker
        {
            SuggestedStartLocation = PickerLocationId.DocumentsLibrary,
            SuggestedFileName = "ftbquests",
            DefaultFileExtension = ".zip",
        };

        picker.FileTypeChoices.Add("FTB Quests Archive", new List<string> { ".zip" });

        var hwnd = WindowNative.GetWindowHandle(this);
        InitializeWithWindow.Initialize(picker, hwnd);

        var file = await picker.PickSaveFileAsync();
        if (file is null)
        {
            return;
        }

        CachedFileManager.DeferUpdates(file);

        try
        {
            await exporter.ExportAsync(currentPack, file.Path);
            var updateStatus = await CachedFileManager.CompleteUpdatesAsync(file);

            if (updateStatus == FileUpdateStatus.Complete)
            {
                ShowToast(
                    "Export complete",
                    $"Saved to {file.Path}",
                    InfoBarSeverity.Success);
            }
            else
            {
                ShowToast(
                    "Export completed with warnings",
                    $"Saved to {file.Path} (status: {updateStatus})",
                    InfoBarSeverity.Warning);
            }
        }
        catch (Exception ex)
        {
            await CachedFileManager.CompleteUpdatesAsync(file);
            await ShowExportErrorAsync(ex);
        }
    }

    private async Task ShowImportErrorAsync(Exception ex)
    {
        if (Content is not FrameworkElement rootElement)
        {
            return;
        }

        var dialog = new ContentDialog
        {
            Title = title,
            Content = message,
            CloseButtonText = "OK",
            XamlRoot = rootElement.XamlRoot,
        };

        await dialog.ShowAsync();
    }

    private async Task<LootViewModels.LootTableManagerViewModel> EnsureLootManagerAsync()
    {
        if (lootManager is not null)
        {
            return lootManager;
        }

        RegistryDatabase registry = await GetRegistryAsync();
        var tableValidator = new LootTableValidator(registry);
        var groupValidator = new LootTableGroupValidator();

        lootManager = new LootViewModels.LootTableManagerViewModel(registry, tableValidator, groupValidator);
        lootManager.Load(Array.Empty<LootTable>(), Array.Empty<LootTableGroup>());
        return lootManager;
    }

    private Task<RegistryDatabase> GetRegistryAsync()
    {
        registryTask ??= LoadRegistryAsync();
        return registryTask;
    }

    private async Task<RegistryDatabase> LoadRegistryAsync()
    {
        string baseDirectory = AppContext.BaseDirectory;
        string registryFolder = Path.Combine(baseDirectory, "data", "minecraft_registry");
        if (!Directory.Exists(registryFolder))
        {
            throw new DirectoryNotFoundException($"Registry folder '{registryFolder}' was not found.");
        }

        var importer = new RegistryImporter();
        return await importer.LoadFromProbeAsync(registryFolder).ConfigureAwait(false);
    private async Task ShowExportErrorAsync(Exception ex)
    {
        if (Content is not FrameworkElement rootElement)
        {
            return;
        }

        if (ToastBar is not null)
        {
            ToastBar.IsOpen = false;
        }

        var dialog = new ContentDialog
        {
            Title = "Export failed",
            Content = ex.Message,
            CloseButtonText = "OK",
            XamlRoot = rootElement.XamlRoot,
        };

        await dialog.ShowAsync();
    }

    private void ShowToast(string title, string message, InfoBarSeverity severity)
    {
        if (ToastBar is null)
        {
            return;
        }

        ToastBar.IsOpen = false;
        ToastBar.Title = title;
        ToastBar.Message = message;
        ToastBar.Severity = severity;
        ToastBar.IsOpen = true;
    }
}
