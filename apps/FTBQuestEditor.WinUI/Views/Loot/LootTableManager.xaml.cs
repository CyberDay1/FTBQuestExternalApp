// <copyright file="LootTableManager.xaml.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using FTBQuestEditor.WinUI.ViewModels.Loot;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace FTBQuestEditor.WinUI.Views.Loot;

public sealed partial class LootTableManager : Page
{
    public LootTableManager()
    {
        InitializeComponent();
    }

    public LootTableManagerViewModel? ViewModel
    {
        get => DataContext as LootTableManagerViewModel;
        set => DataContext = value;
    }

    private async void OnRenameGroup(object sender, RoutedEventArgs e)
    {
        if (ViewModel?.SelectedGroup is null)
        {
            return;
        }

        string currentName = ViewModel.SelectedGroup.Name;
        var textBox = new TextBox { Text = currentName, PlaceholderText = "Group name" };
        var dialog = new ContentDialog
        {
            Title = "Rename group",
            PrimaryButtonText = "Save",
            SecondaryButtonText = "Cancel",
            DefaultButton = ContentDialogButton.Primary,
            Content = textBox,
            XamlRoot = XamlRoot,
        };

        ContentDialogResult result = await dialog.ShowAsync();
        if (result != ContentDialogResult.Primary)
        {
            return;
        }

        string newName = textBox.Text.Trim();
        if (string.Equals(newName, currentName, StringComparison.Ordinal))
        {
            return;
        }

        try
        {
            ViewModel.RenameGroup(ViewModel.SelectedGroup, newName);
        }
        catch (Exception ex)
        {
            await ShowErrorAsync(ex.Message);
        }
    }

    private void OnAddGroup(object sender, RoutedEventArgs e)
    {
        ViewModel?.AddGroup();
    }

    private void OnDeleteGroup(object sender, RoutedEventArgs e)
    {
        if (ViewModel?.SelectedGroup is null)
        {
            return;
        }

        ViewModel.DeleteGroup(ViewModel.SelectedGroup);
    }

    private void OnAddTable(object sender, RoutedEventArgs e)
    {
        ViewModel?.AddTable();
    }

    private void OnDeleteTable(object sender, RoutedEventArgs e)
    {
        if (ViewModel?.SelectedTable is null)
        {
            return;
        }

        ViewModel.DeleteTable(ViewModel.SelectedTable);
    }

    private async void OnApplyTable(object sender, RoutedEventArgs e)
    {
        try
        {
            ViewModel?.SyncEditorToSelectedTable();
        }
        catch (Exception ex)
        {
            await ShowErrorAsync(ex.Message);
        }
    }

    private void OnMoveTableToGroup(object sender, RoutedEventArgs e)
    {
        if (ViewModel?.SelectedTable is null)
        {
            return;
        }

        ViewModel.AssignTableToGroup(ViewModel.SelectedTable, ViewModel.SelectedGroup);
    }

    private void OnShowUngrouped(object sender, RoutedEventArgs e)
    {
        if (ViewModel is not null)
        {
            ViewModel.SelectedGroup = null;
        }
    }

    private async System.Threading.Tasks.Task ShowErrorAsync(string message)
    {
        var dialog = new ContentDialog
        {
            Title = "Loot Table Manager",
            Content = message,
            CloseButtonText = "OK",
            XamlRoot = XamlRoot,
        };

        await dialog.ShowAsync();
    }
}
