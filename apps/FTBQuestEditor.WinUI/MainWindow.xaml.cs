using System;
using System.Threading.Tasks;
using FTBQuestEditor.WinUI.Views;
using FTBQuests.IO;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Windows.Storage.Pickers;
using WinRT.Interop;

namespace FTBQuestEditor.WinUI;

public sealed partial class MainWindow : Window
{
    private readonly QuestPackLoader loader = new();
    private readonly PresetStore presetStore = new();

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
        }
        catch (Exception ex)
        {
            await ShowImportErrorAsync(ex);
        }
    }

    private async void OnPresetsClicked(object sender, RoutedEventArgs e)
    {
        if (Content is not FrameworkElement rootElement)
        {
            return;
        }

        var dialog = new PresetManagerDialog(
            presetStore,
            () => Navigator?.ChapterViewModel.Pack,
            pack => Navigator?.LoadQuestPack(pack),
            rootElement);

        await dialog.ShowAsync();
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
}
