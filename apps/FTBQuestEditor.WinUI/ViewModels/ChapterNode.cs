// <copyright file="ChapterNode.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using CommunityToolkit.Mvvm.ComponentModel;
using FTBQuestExternalApp.Codecs.Model;
using FTBQuests.Validation;

namespace FTBQuestEditor.WinUI.ViewModels;

/// <summary>
/// Represents either a chapter group or an individual chapter within the quest tree.
/// </summary>
public sealed class ChapterNode : ObservableObject
{
    private readonly List<ChapterNode> _allChildren = new();
    private ValidationSeverity _validationSeverity = ValidationSeverity.Info;
    private bool _isExpanded;

    public ChapterNode(string id, string title, bool isGroup, Chapter? chapter)
    {
        Id = id;
        Title = title;
        IsGroup = isGroup;
        Chapter = chapter;
        Children = new ObservableCollection<ChapterNode>();
    }

    public string Id { get; }

    public string Title { get; }

    public bool IsGroup { get; }

    public Chapter? Chapter { get; }

    public ChapterNode? Parent { get; private set; }

    public ObservableCollection<ChapterNode> Children { get; }

    public IReadOnlyList<ChapterNode> AllChildren => _allChildren;

    public bool IsExpanded
    {
        get => _isExpanded;
        set => SetProperty(ref _isExpanded, value);
    }

    public ValidationSeverity ValidationSeverity
    {
        get => _validationSeverity;
        private set => SetProperty(ref _validationSeverity, value);
    }

    public ChapterValidationState ValidationState => ValidationSeverity switch
    {
        ValidationSeverity.Error => ChapterValidationState.Error,
        ValidationSeverity.Warning => ChapterValidationState.Warning,
        _ => ChapterValidationState.None,
    };

    public bool HasIssues => ValidationState != ChapterValidationState.None;

    public string BadgeGlyph => ValidationState switch
    {
        ChapterValidationState.Error => "!",
        ChapterValidationState.Warning => "?",
        _ => string.Empty,
    };

    public void AddChild(ChapterNode child)
    {
        ArgumentNullException.ThrowIfNull(child);

        child.Parent = this;
        _allChildren.Add(child);
        Children.Add(child);
    }

    public void SetValidationSeverity(ValidationSeverity severity)
    {
        ValidationSeverity = severity;
    }

    public void UpdateAggregateValidation()
    {
        if (!IsGroup)
        {
            return;
        }

        foreach (var child in _allChildren)
        {
            child.UpdateAggregateValidation();
        }

        var maxSeverity = _allChildren
            .Select(child => child.ValidationSeverity)
            .DefaultIfEmpty(ValidationSeverity.Info)
            .Max();

        ValidationSeverity = maxSeverity;
    }

    public void ResetChildren()
    {
        Children.Clear();
        foreach (var child in _allChildren)
        {
            child.ResetChildren();
            Children.Add(child);
        }
    }

    public bool ApplyFilter(string? filterText)
    {
        if (string.IsNullOrWhiteSpace(filterText))
        {
            ResetChildren();
            return true;
        }

        var comparison = StringComparison.OrdinalIgnoreCase;
        var matchesSelf = Title.Contains(filterText, comparison);

        if (!IsGroup)
        {
            return matchesSelf;
        }

        Children.Clear();
        var anyChildMatches = false;
        foreach (var child in _allChildren)
        {
            if (child.ApplyFilter(filterText))
            {
                Children.Add(child);
                anyChildMatches = true;
            }
        }

        return matchesSelf || anyChildMatches;
    }

    public IEnumerable<ChapterNode> EnumerateDescendants()
    {
        foreach (var child in _allChildren)
        {
            yield return child;
            foreach (var descendant in child.EnumerateDescendants())
            {
                yield return descendant;
            }
        }
    }
}

/// <summary>
/// Represents the simplified validation state displayed for chapter nodes.
/// </summary>
public enum ChapterValidationState
{
    None,
    Warning,
    Error,
}
