using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using FTBQuestEditor.WinUI.Views;
using FTBQuests.IO;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Windows.Storage.Pickers;
using Windows.Storage;
using Windows.Storage.Provider;
using WinRT.Interop;

namespace FTBQuestEditor.WinUI;

public sealed partial class MainWindow : Window
{
    private readonly QuestPackLoader loader = new();
    private readonly QuestPackExporter exporter = new();
    private QuestPack? currentPack;

    public MainWindow()
    {
        InitializeComponent();
    }

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
            await ShowImportErrorAsync(ex);
        }
    }

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
            Title = "Import failed",
            Content = ex.Message,
            CloseButtonText = "OK",
            XamlRoot = rootElement.XamlRoot,
        };

        await dialog.ShowAsync();
    }

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
