// <copyright file="LootTableEditorViewModel.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.ComponentModel;
using System.Linq;
using CommunityToolkit.Mvvm.ComponentModel;
using FTBQuestExternalApp.Codecs.Model;
using FTBQuests.Loot;
using FTBQuests.Registry;
using FTBQuests.Registry.Model;
using FTBQuests.Validation.Validators;

namespace FTBQuestEditor.WinUI.ViewModels;

/// <summary>
/// View model that powers the loot table editor experience.
/// </summary>
public sealed partial class LootTableEditorViewModel : ObservableObject
{
    public const string AllModsFilter = "All Mods";

    private readonly RegistryDatabase registry;
    private readonly LootTableValidator validator;

    private string tableName = "new_loot_table";
    private string selectedModId = AllModsFilter;
    private bool suppressValidation;

    public LootTableEditorViewModel(RegistryDatabase registry, LootTableValidator validator)
    {
        this.registry = registry ?? throw new ArgumentNullException(nameof(registry));
        this.validator = validator ?? throw new ArgumentNullException(nameof(validator));

        ModFilters = new ObservableCollection<string>();
        AvailableItems = new ObservableCollection<RegistryItemViewModel>();
        Entries = new ObservableCollection<LootEntryViewModel>();
        ValidationMessages = new ObservableCollection<string>();

        Entries.CollectionChanged += OnEntriesChanged;

        InitializeFilters();
        RefreshAvailableItems();
        UpdateValidation();
    }

    public ObservableCollection<string> ModFilters { get; }

    public ObservableCollection<RegistryItemViewModel> AvailableItems { get; }

    public ObservableCollection<LootEntryViewModel> Entries { get; }

    public ObservableCollection<string> ValidationMessages { get; }

    public string TableName
    {
        get => tableName;
        set
        {
            if (string.IsNullOrWhiteSpace(value))
            {
                throw new ArgumentException("Table name cannot be empty.", nameof(value));
            }

            if (SetProperty(ref tableName, value))
            {
                if (!suppressValidation)
                {
                    UpdateValidation();
                }
            }
        }
    }

    public string SelectedModId
    {
        get => selectedModId;
        set
        {
            if (SetProperty(ref selectedModId, value))
            {
                RefreshAvailableItems();
                if (!suppressValidation)
                {
                    UpdateValidation();
                }
            }
        }
    }

    public void ReloadRegistry()
    {
        InitializeFilters();
        RefreshAvailableItems();
        UpdateValidation();
    }

    public void AddItemToTable(RegistryItemViewModel item)
    {
        ArgumentNullException.ThrowIfNull(item);
        var entry = CreateEntryViewModel(item.Id);
        Entries.Add(entry);
    }

    public bool DeleteRegistryItem(string itemId)
    {
        ArgumentException.ThrowIfNullOrEmpty(itemId);

        bool removed = registry.RemoveItem(new Identifier(itemId));
        if (removed)
        {
            ReloadRegistry();
        }

        return removed;
    }

    public int DeleteItemsForMod(string modId)
    {
        ArgumentException.ThrowIfNullOrEmpty(modId);

        int removed = registry.RemoveItemsByMod(modId);
        if (removed > 0)
        {
            ReloadRegistry();
        }

        return removed;
    }

    public int GetRegistryItemCountForMod(string modId)
    {
        ArgumentException.ThrowIfNullOrEmpty(modId);
        return registry.GetItemsByMod(modId).Count;
    }

    public void RemoveEntry(LootEntryViewModel entry)
    {
        ArgumentNullException.ThrowIfNull(entry);
        Entries.Remove(entry);
    }

    public void Load(LootTable table)
    {
        ArgumentNullException.ThrowIfNull(table);

        suppressValidation = true;
        try
        {
            TableName = table.Name;
            Entries.Clear();
            foreach (LootEntry entry in table.Entries)
            {
                var viewModel = CreateEntryViewModel(entry.Id.ToString());
                viewModel.Weight = entry.Weight;
                viewModel.CountMin = entry.CountMin;
                viewModel.CountMax = entry.CountMax;
                viewModel.Conditions = entry.Conditions;
                Entries.Add(viewModel);
            }
        }
        finally
        {
            suppressValidation = false;
        }

        UpdateValidation();
    }

    public LootTable BuildTable()
    {
        var errors = new List<string>();
        LootTable? table = TryBuildTable(errors);
        if (table is null)
        {
            throw new InvalidOperationException(string.Join(Environment.NewLine, errors));
        }

        return table;
    }

    public string Save(string rootDirectory)
    {
        LootTable table = BuildTable();
        var builder = new LootTableBuilder(table.Name);
        foreach (LootEntry entry in table.Entries)
        {
            builder.AddEntry(entry.Id, entry.Weight, entry.CountMin, entry.CountMax, entry.Conditions);
        }

        string path = builder.Save(rootDirectory);
        UpdateValidation();
        return path;
    }

    private LootEntryViewModel CreateEntryViewModel(string itemId)
    {
        return new LootEntryViewModel(itemId);
    }

    private void InitializeFilters()
    {
        suppressValidation = true;
        try
        {
            ModFilters.Clear();
            ModFilters.Add(AllModsFilter);
            foreach (string mod in registry.GetModIdentifiers())
            {
                ModFilters.Add(mod);
            }

            if (!ModFilters.Any(mod => string.Equals(mod, selectedModId, StringComparison.OrdinalIgnoreCase)))
            {
                selectedModId = AllModsFilter;
                OnPropertyChanged(nameof(SelectedModId));
            }
        }
        finally
        {
            suppressValidation = false;
        }
    }

    private void RefreshAvailableItems()
    {
        AvailableItems.Clear();
        IEnumerable<RegistryItem> items = SelectedModId == AllModsFilter
            ? registry.Items
            : registry.GetItemsByMod(SelectedModId);

        foreach (RegistryItem item in items)
        {
            AvailableItems.Add(new RegistryItemViewModel(item.Id, item.DisplayName, item.SourceModId));
        }
    }

    private void OnEntriesChanged(object? sender, NotifyCollectionChangedEventArgs e)
    {
        if (e.NewItems is not null)
        {
            foreach (var item in e.NewItems.OfType<LootEntryViewModel>())
            {
                item.PropertyChanged += OnEntryPropertyChanged;
            }
        }

        if (e.OldItems is not null)
        {
            foreach (var item in e.OldItems.OfType<LootEntryViewModel>())
            {
                item.PropertyChanged -= OnEntryPropertyChanged;
            }
        }

        if (!suppressValidation)
        {
            UpdateValidation();
        }
    }

    private void OnEntryPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (!suppressValidation)
        {
            UpdateValidation();
        }
    }

    private LootTable? TryBuildTable(ICollection<string> errors)
    {
        if (string.IsNullOrWhiteSpace(TableName))
        {
            errors.Add("Loot table name must be provided.");
            return null;
        }

        var table = new LootTable(TableName);
        foreach (LootEntryViewModel entry in Entries)
        {
            if (string.IsNullOrWhiteSpace(entry.ItemId))
            {
                errors.Add("Entry is missing an item identifier.");
                continue;
            }

            if (!Identifier.IsValid(entry.ItemId))
            {
                errors.Add($"Entry identifier '{entry.ItemId}' is not valid.");
                continue;
            }

            if (entry.Weight <= 0)
            {
                errors.Add($"Entry '{entry.ItemId}' must have a positive weight.");
                continue;
            }

            if (entry.CountMin < 0)
            {
                errors.Add($"Entry '{entry.ItemId}' must have a non-negative minimum count.");
                continue;
            }

            if (entry.CountMax < entry.CountMin)
            {
                errors.Add($"Entry '{entry.ItemId}' must have a maximum count greater than or equal to the minimum.");
                continue;
            }

            table.Entries.Add(new LootEntry(new Identifier(entry.ItemId), entry.Weight, entry.CountMin, entry.CountMax, string.IsNullOrWhiteSpace(entry.Conditions) ? null : entry.Conditions));
        }

        if (errors.Count > 0)
        {
            return null;
        }

        return table;
    }

    private void UpdateValidation()
    {
        var messages = new List<string>();
        LootTable? table = TryBuildTable(messages);
        if (table is not null)
        {
            messages.AddRange(validator.Validate(table));
        }

        ValidationMessages.Clear();
        foreach (string message in messages.Distinct())
        {
            ValidationMessages.Add(message);
        }
    }

    /// <summary>
    /// Represents a registry item option shown in the editor.
    /// </summary>
    public sealed class RegistryItemViewModel
    {
        public RegistryItemViewModel(string id, string displayName, string modId)
        {
            Id = id;
            DisplayName = displayName;
            ModId = modId;
        }

        public string Id { get; }

        public string DisplayName { get; }

        public string ModId { get; }

        public string Label => string.IsNullOrEmpty(DisplayName) ? Id : $"{DisplayName} ({Id})";
    }

    /// <summary>
    /// Represents an editable loot table entry.
    /// </summary>
    public sealed partial class LootEntryViewModel : ObservableObject
    {
        private string itemId;
        private int weight = 1;
        private int countMin = 1;
        private int countMax = 1;
        private string? conditions;

        public LootEntryViewModel(string itemId)
        {
            this.itemId = itemId;
        }

        public string ItemId
        {
            get => itemId;
            set => SetProperty(ref itemId, value);
        }

        public int Weight
        {
            get => weight;
            set => SetProperty(ref weight, value);
        }

        public int CountMin
        {
            get => countMin;
            set => SetProperty(ref countMin, value);
        }

        public int CountMax
        {
            get => countMax;
            set => SetProperty(ref countMax, value);
        }

        public string? Conditions
        {
            get => conditions;
            set => SetProperty(ref conditions, value);
        }
    }
}
