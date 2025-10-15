// <copyright file="LootTableEditor.xaml.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Threading.Tasks;
using FTBQuestEditor.WinUI.ViewModels;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace FTBQuestEditor.WinUI.Views;

public sealed partial class LootTableEditor : Page
{
    public LootTableEditor()
    {
        InitializeComponent();
    }

    public LootTableEditorViewModel? ViewModel
    {
        get => DataContext as LootTableEditorViewModel;
        set => DataContext = value;
    }

    private async void OnDeleteRegistryItemClicked(object sender, RoutedEventArgs e)
    {
        if (ViewModel is null)
        {
            return;
        }

        if (sender is MenuFlyoutItem menuItem && menuItem.DataContext is LootTableEditorViewModel.RegistryItemViewModel item)
        {
            string display = string.IsNullOrWhiteSpace(item.DisplayName) ? item.Id : item.DisplayName;
            string message = $"Delete '{display}' from the registry? This will remove 1 item.";

            if (await ShowDeleteConfirmationAsync("Delete Item", message))
            {
                ViewModel.DeleteRegistryItem(item.Id);
            }
        }
    }

    private async void OnDeleteModItemsClicked(object sender, RoutedEventArgs e)
    {
        if (ViewModel is null)
        {
            return;
        }

        if (sender is MenuFlyoutItem menuItem && menuItem.DataContext is LootTableEditorViewModel.RegistryItemViewModel item)
        {
            int count = ViewModel.GetRegistryItemCountForMod(item.ModId);
            if (count == 0)
            {
                return;
            }

            string message = count == 1
                ? $"Delete the last remaining item contributed by '{item.ModId}'?"
                : $"Delete all {count} items contributed by '{item.ModId}'?";

            if (await ShowDeleteConfirmationAsync("Delete Mod Items", message))
            {
                ViewModel.DeleteItemsForMod(item.ModId);
            }
        }
    }

    private async Task<bool> ShowDeleteConfirmationAsync(string title, string message)
    {
        if (XamlRoot is null)
        {
            return false;
        }

        var dialog = new ContentDialog
        {
            Title = title,
            Content = message,
            PrimaryButtonText = "Delete",
            CloseButtonText = "Cancel",
            DefaultButton = ContentDialogButton.Close,
            XamlRoot = XamlRoot,
        };

        ContentDialogResult result = await dialog.ShowAsync();
        return result == ContentDialogResult.Primary;
    }
}
