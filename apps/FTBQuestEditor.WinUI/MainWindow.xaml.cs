using System;
using System.IO;
using System.Threading.Tasks;
using FTBQuests.IO;
using FTBQuests.IO.Presets;
using FTBQuestEditor.WinUI.ViewModels;
using FTBQuestEditor.WinUI.Views;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Windows.Storage.Pickers;
using WinRT.Interop;

namespace FTBQuestEditor.WinUI;

public sealed partial class MainWindow : Window
{
    private readonly QuestPackLoader loader = new();

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
