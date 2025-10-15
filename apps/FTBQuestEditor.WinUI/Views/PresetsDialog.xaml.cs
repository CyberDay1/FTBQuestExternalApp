using System.Threading.Tasks;
using FTBQuestEditor.WinUI.ViewModels;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace FTBQuestEditor.WinUI.Views;

public sealed partial class PresetsDialog : ContentDialog
{
    public PresetsDialog()
    {
        InitializeComponent();
    }

    public PresetsDialogViewModel? ViewModel
    {
        get => DataContext as PresetsDialogViewModel;
        set => DataContext = value;
    }

    private async void OnDeleteSlotClicked(object sender, RoutedEventArgs e)
    {
        if (ViewModel is null)
        {
            return;
        }

        if (sender is not Button button || button.DataContext is not PresetsDialogViewModel.PresetSlotViewModel slot)
        {
            return;
        }

        if (await ShowDeleteConfirmationAsync(slot.Name))
        {
            ViewModel.DeleteSlot(slot);
        }
    }

    private void OnDialogOpened(ContentDialog sender, ContentDialogOpenedEventArgs args)
    {
        ViewModel?.LoadSlots();
    }

    private async Task<bool> ShowDeleteConfirmationAsync(string slotName)
    {
        if (XamlRoot is null)
        {
            return false;
        }

        var dialog = new ContentDialog
        {
            Title = "Delete Preset",
            Content = $"Delete preset '{slotName}'?",
            PrimaryButtonText = "Delete",
            CloseButtonText = "Cancel",
            DefaultButton = ContentDialogButton.Close,
            XamlRoot = XamlRoot,
        };

        ContentDialogResult result = await dialog.ShowAsync();
        return result == ContentDialogResult.Primary;
    }
}
