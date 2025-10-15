using System;
using System.IO;
using System.Threading.Tasks;
using FTBQuests.IO;
using FTBQuests.Loot;
using FTBQuests.Registry;
using FTBQuests.Validation.Validators;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Windows.Storage.Pickers;
using WinRT.Interop;
using LootViews = FTBQuestEditor.WinUI.Views.Loot;
using LootViewModels = FTBQuestEditor.WinUI.ViewModels.Loot;

namespace FTBQuestEditor.WinUI;

public sealed partial class MainWindow : Window
{
    private readonly QuestPackLoader loader = new();
    private Task<RegistryDatabase>? registryTask;
    private LootViewModels.LootTableManagerViewModel? lootManager;

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
            await ShowErrorAsync("Import failed", ex.Message);
        }
    }

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
    }
}
