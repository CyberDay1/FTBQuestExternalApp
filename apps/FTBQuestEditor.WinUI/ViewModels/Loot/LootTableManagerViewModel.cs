using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="LootTableManagerViewModel.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using CommunityToolkit.Mvvm.ComponentModel;
using FTBQuestEditor.WinUI.ViewModels;
using FTBQuests.Loot;
using FTBQuests.Registry;
using FTBQuests.Validation.Validators;

namespace FTBQuestEditor.WinUI.ViewModels.Loot;

/// <summary>
/// View model that orchestrates loot table and group management.
/// </summary>
public sealed partial class LootTableManagerViewModel : ObservableObject
{
    private readonly RegistryDatabase registry;
    private readonly LootTableValidator tableValidator;
    private readonly LootTableGroupValidator groupValidator;

    /// <summary>
    /// Initializes a new instance of the <see cref="LootTableManagerViewModel"/> class.
    /// </summary>
    public LootTableManagerViewModel(RegistryDatabase registry, LootTableValidator tableValidator, LootTableGroupValidator groupValidator)
    {
        this.registry = registry ?? throw new ArgumentNullException(nameof(registry));
        this.tableValidator = tableValidator ?? throw new ArgumentNullException(nameof(tableValidator));
        this.groupValidator = groupValidator ?? throw new ArgumentNullException(nameof(groupValidator));

        Editor = new LootTableEditorViewModel(this.registry, this.tableValidator);
        Groups = new ObservableCollection<GroupViewModel>();
        AllTables = new ObservableCollection<LootTableItemViewModel>();
        VisibleTables = new ObservableCollection<LootTableItemViewModel>();
    }

    /// <summary>
    /// Gets the embedded loot table editor view model.
    /// </summary>
    public LootTableEditorViewModel Editor { get; }

    /// <summary>
    /// Gets the collection of available groups.
    /// </summary>
    public ObservableCollection<GroupViewModel> Groups { get; }

    private ObservableCollection<LootTableItemViewModel> AllTables { get; }

    /// <summary>
    /// Gets the tables visible for the currently selected group.
    /// </summary>
    public ObservableCollection<LootTableItemViewModel> VisibleTables { get; }

    /// <summary>
    /// Gets or sets the currently selected group. A <c>null</c> value represents ungrouped tables.
    /// </summary>
    [ObservableProperty]
    private GroupViewModel? selectedGroup;

    partial void OnSelectedGroupChanged(GroupViewModel? value)
    {
        RefreshVisibleTables();
    }

    /// <summary>
    /// Gets or sets the currently selected table.
    /// </summary>
    [ObservableProperty]
    private LootTableItemViewModel? selectedTable;

    partial void OnSelectedTableChanged(LootTableItemViewModel? value)
    {
        if (value is null)
        {
            return;
        }

        Editor.Load(CloneTable(value.Table));
    }

    /// <summary>
    /// Loads the manager with pre-existing tables and groups.
    /// </summary>
    public void Load(IEnumerable<LootTable> tables, IEnumerable<LootTableGroup> groups)
    {
        ArgumentNullException.ThrowIfNull(tables);
        ArgumentNullException.ThrowIfNull(groups);

        SelectedTable = null;
        SelectedGroup = null;
        AllTables.Clear();
        Groups.Clear();
        VisibleTables.Clear();

        var tableLookup = new Dictionary<string, LootTableItemViewModel>(StringComparer.OrdinalIgnoreCase);
        foreach (LootTable table in tables)
        {
            var item = new LootTableItemViewModel(CloneTable(table));
            AllTables.Add(item);
            tableLookup[item.Name] = item;
        }

        foreach (LootTableGroup group in groups)
        {
            var groupViewModel = new GroupViewModel(group.Name);
            Groups.Add(groupViewModel);

            foreach (string tableName in group.TableNames)
            {
                if (tableLookup.TryGetValue(tableName, out LootTableItemViewModel? tableViewModel))
                {
                    AssignTableToGroupInternal(tableViewModel, groupViewModel);
                }
            }
        }

        RefreshVisibleTables();
    }

    /// <summary>
    /// Adds a new group to the manager.
    /// </summary>
    public GroupViewModel AddGroup(string? baseName = null)
    {
        string groupName = GenerateUniqueGroupName(baseName ?? "new_group");
        var group = new GroupViewModel(groupName);
        Groups.Add(group);
        SelectedGroup = group;
        return group;
    }

    /// <summary>
    /// Renames a group to the specified value.
    /// </summary>
    public void RenameGroup(GroupViewModel group, string newName)
    {
        ArgumentNullException.ThrowIfNull(group);
        ArgumentException.ThrowIfNullOrEmpty(newName);

        if (Groups.Any(existing => !ReferenceEquals(existing, group) && string.Equals(existing.Name, newName, StringComparison.OrdinalIgnoreCase)))
        {
            throw new InvalidOperationException($"A group named '{newName}' already exists.");
        }

        group.Name = newName;
    }

    /// <summary>
    /// Deletes a group and returns its tables to the ungrouped pool.
    /// </summary>
    public void DeleteGroup(GroupViewModel group)
    {
        ArgumentNullException.ThrowIfNull(group);

        foreach (LootTableItemViewModel table in group.Tables.ToList())
        {
            AssignTableToGroupInternal(table, null);
        }

        Groups.Remove(group);
        if (ReferenceEquals(SelectedGroup, group))
        {
            SelectedGroup = null;
        }

        RefreshVisibleTables();
    }

    /// <summary>
    /// Adds a new loot table under the currently selected group.
    /// </summary>
    public LootTableItemViewModel AddTable(string? baseName = null)
    {
        string tableName = GenerateUniqueTableName(baseName ?? "new_loot_table");
        var table = new LootTable(tableName);
        var tableViewModel = new LootTableItemViewModel(table);
        AllTables.Add(tableViewModel);
        AssignTableToGroupInternal(tableViewModel, SelectedGroup);
        RefreshVisibleTables();

        SelectedTable = tableViewModel;
        Editor.Load(CloneTable(table));
        return tableViewModel;
    }

    /// <summary>
    /// Removes the specified table from the manager.
    /// </summary>
    public void DeleteTable(LootTableItemViewModel table)
    {
        ArgumentNullException.ThrowIfNull(table);

        AssignTableToGroupInternal(table, null);
        AllTables.Remove(table);

        if (ReferenceEquals(SelectedTable, table))
        {
            SelectedTable = null;
        }

        RefreshVisibleTables();
    }

    /// <summary>
    /// Assigns the provided table to a group.
    /// </summary>
    public void AssignTableToGroup(LootTableItemViewModel table, GroupViewModel? group)
    {
        ArgumentNullException.ThrowIfNull(table);
        AssignTableToGroupInternal(table, group);
        RefreshVisibleTables();
    }

    /// <summary>
    /// Applies the current editor state onto the selected table.
    /// </summary>
    public void SyncEditorToSelectedTable()
    {
        if (SelectedTable is null)
        {
            return;
        }

        LootTable built = Editor.BuildTable();
        if (!string.Equals(built.Name, SelectedTable.Name, StringComparison.OrdinalIgnoreCase) &&
            AllTables.Any(table => !ReferenceEquals(table, SelectedTable) && string.Equals(table.Name, built.Name, StringComparison.OrdinalIgnoreCase)))
        {
            throw new InvalidOperationException($"A loot table named '{built.Name}' already exists.");
        }

        SelectedTable.ReplaceTable(CloneTable(built));
        RefreshVisibleTables();
    }

    /// <summary>
    /// Builds a snapshot of the current loot tables.
    /// </summary>
    public IReadOnlyList<LootTable> BuildTables()
    {
        return AllTables
            .Select(table => CloneTable(table.Table))
            .ToList();
    }

    /// <summary>
    /// Builds the loot groups from the current state.
    /// </summary>
    public IReadOnlyList<LootTableGroup> BuildGroups()
    {
        var result = new List<LootTableGroup>();
        foreach (GroupViewModel group in Groups)
        {
            var clone = new LootTableGroup(group.Name);
            foreach (LootTableItemViewModel table in group.Tables)
            {
                clone.TableNames.Add(table.Name);
            }

            if (clone.TableNames.Count > 0)
            {
                result.Add(clone);
            }
        }

        return result;
    }

    /// <summary>
    /// Validates the configured groups against the known tables.
    /// </summary>
    public IReadOnlyList<string> ValidateGroups()
    {
        var tables = BuildTables();
        var messages = new List<string>();
        foreach (LootTableGroup group in BuildGroups())
        {
            messages.AddRange(groupValidator.Validate(group, tables));
        }

        return messages;
    }

    private void AssignTableToGroupInternal(LootTableItemViewModel table, GroupViewModel? group)
    {
        if (table.Group is GroupViewModel previous)
        {
            previous.Tables.Remove(table);
        }

        table.Group = group;
        if (group is not null && !group.Tables.Contains(table))
        {
            group.Tables.Add(table);
        }
    }

    private void RefreshVisibleTables()
    {
        IEnumerable<LootTableItemViewModel> source = SelectedGroup is null
            ? AllTables.Where(table => table.Group is null)
            : SelectedGroup.Tables;

        VisibleTables.Clear();
        foreach (LootTableItemViewModel table in source.OrderBy(table => table.Name, StringComparer.OrdinalIgnoreCase))
        {
            VisibleTables.Add(table);
        }
    }

    private string GenerateUniqueGroupName(string baseName)
    {
        string candidate = baseName;
        int suffix = 1;
        while (Groups.Any(group => string.Equals(group.Name, candidate, StringComparison.OrdinalIgnoreCase)))
        {
            candidate = $"{baseName}_{suffix++}";
        }

        return candidate;
    }

    private string GenerateUniqueTableName(string baseName)
    {
        string candidate = baseName;
        int suffix = 1;
        while (AllTables.Any(table => string.Equals(table.Name, candidate, StringComparison.OrdinalIgnoreCase)))
        {
            candidate = $"{baseName}_{suffix++}";
        }

        return candidate;
    }

    private static LootTable CloneTable(LootTable table)
    {
        ArgumentNullException.ThrowIfNull(table);
        var clone = new LootTable(table.Name);
        foreach (LootEntry entry in table.Entries)
        {
            clone.Entries.Add(new LootEntry(entry.Id, entry.Weight, entry.CountMin, entry.CountMax, entry.Conditions));
        }

        return clone;
    }

    /// <summary>
    /// Represents a loot table group in the UI.
    /// </summary>
    public sealed partial class GroupViewModel : ObservableObject
    {
        private string name;

        public GroupViewModel(string name)
        {
            ArgumentException.ThrowIfNullOrEmpty(name);
            this.name = name;
            Tables = new ObservableCollection<LootTableItemViewModel>();
        }

        public string Name
        {
            get => name;
            internal set => SetProperty(ref name, value);
        }

        public ObservableCollection<LootTableItemViewModel> Tables { get; }
    }

    /// <summary>
    /// Represents a loot table entry shown in the manager list.
    /// </summary>
    public sealed partial class LootTableItemViewModel : ObservableObject
    {
        private LootTable table;
        private GroupViewModel? group;

        public LootTableItemViewModel(LootTable table)
        {
            this.table = table ?? throw new ArgumentNullException(nameof(table));
        }

        public LootTable Table
        {
            get => table;
            private set
            {
                if (SetProperty(ref table, value))
                {
                    OnPropertyChanged(nameof(Name));
                }
            }
        }

        public string Name => Table.Name;

        public GroupViewModel? Group
        {
            get => group;
            internal set => SetProperty(ref group, value);
        }

        public void ReplaceTable(LootTable replacement)
        {
            ArgumentNullException.ThrowIfNull(replacement);
            Table = replacement;
        }
    }
}
