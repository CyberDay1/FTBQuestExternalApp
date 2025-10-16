using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="ChapterListViewModel.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Globalization;
using System.Linq;
using System.Windows.Input;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using FTBQuestExternalApp.Codecs.Model;
using FTBQuests.IO;
using FTBQuests.Validation;
using Newtonsoft.Json.Linq;

namespace FTBQuestEditor.WinUI.ViewModels;

/// <summary>
/// Provides an observable tree of chapters and chapter groups that can be rendered in the navigator.
/// </summary>
public sealed class ChapterListViewModel : ObservableObject
{
    private readonly ObservableCollection<ChapterNode> _nodes;
    private readonly List<ChapterNode> _allRootNodes = new();
    private readonly Dictionary<long, ChapterNode> _chapterLookup = new();
    private readonly IReadOnlyList<IValidator> _validators;
    private FTBQuests.IO.QuestPack? _currentPack;
    private ChapterNode? _selectedNode;
    private Chapter? _selectedChapter;
    private string _filterText = string.Empty;

    public ChapterListViewModel()
        : this(new IValidator[]
        {
            new RequiredFieldsValidator(),
            new BrokenReferenceValidator(),
        })
    {
    }

    public ChapterListViewModel(IReadOnlyList<IValidator> validators)
    {
        _validators = validators;
        _nodes = new ObservableCollection<ChapterNode>();
        Nodes = new ReadOnlyObservableCollection<ChapterNode>(_nodes);
        SelectChapterCommand = new RelayCommand<ChapterNode?>(ExecuteSelectChapter);
    }

    public ReadOnlyObservableCollection<ChapterNode> Nodes { get; }

    public ChapterNode? SelectedNode
    {
        get => _selectedNode;
        private set => SetProperty(ref _selectedNode, value);
    }

    public Chapter? SelectedChapter
    {
        get => _selectedChapter;
        private set => SetProperty(ref _selectedChapter, value);
    }

    public string FilterText
    {
        get => _filterText;
        set
        {
            if (SetProperty(ref _filterText, value))
            {
                ApplyFilter();
            }
        }
    }

    public ICommand SelectChapterCommand { get; }

    public event EventHandler<Chapter?>? ChapterSelected;

    public void LoadQuestPack(FTBQuests.IO.QuestPack pack)
    {
        ArgumentNullException.ThrowIfNull(pack);

        _currentPack = pack;
        _allRootNodes.Clear();
        _chapterLookup.Clear();

        foreach (var chapter in pack.Chapters)
        {
            if (chapter is not null)
            {
                _chapterLookup[chapter.Id] = CreateChapterNode(chapter, ValidationSeverity.Info);
            }
        }

        var issues = RunValidators(pack).ToList();
        ApplyValidationIssues(issues);

        var assignedChapters = new HashSet<long>();
        var groupDefinitions = ParseChapterGroups(pack.Metadata.Extra);

        foreach (var group in groupDefinitions)
        {
            var node = BuildGroupNode(group, assignedChapters);
            if (node is not null)
            {
                _allRootNodes.Add(node);
            }
        }

        foreach (var chapter in pack.Chapters)
        {
            if (chapter is null)
            {
                continue;
            }

            if (!assignedChapters.Add(chapter.Id))
            {
                continue;
            }

            if (!_chapterLookup.TryGetValue(chapter.Id, out var node))
            {
                node = CreateChapterNode(chapter, ValidationSeverity.Info);
                _chapterLookup[chapter.Id] = node;
            }

            _allRootNodes.Add(node);
        }

        ApplyFilter();

        var defaultChapter = FindFirstChapterNode();
        if (defaultChapter is not null)
        {
            SetSelection(defaultChapter);
        }
        else
        {
            ClearSelection();
        }
    }

    public void Clear()
    {
        _currentPack = null;
        _allRootNodes.Clear();
        _chapterLookup.Clear();
        _nodes.Clear();
        ClearSelection();
    }

    private void ExecuteSelectChapter(ChapterNode? node)
    {
        if (node is null)
        {
            ClearSelection();
            return;
        }

        if (!node.IsGroup && node.Chapter is not null)
        {
            SetSelection(node);
        }
        else
        {
            SelectedNode = node;
        }
    }

    private void SetSelection(ChapterNode node)
    {
        SelectedNode = node;
        SelectedChapter = node.Chapter;
        ChapterSelected?.Invoke(this, SelectedChapter);
    }

    private void ClearSelection()
    {
        SelectedNode = null;
        SelectedChapter = null;
        ChapterSelected?.Invoke(this, null);
    }

    private void ApplyFilter()
    {
        _nodes.Clear();
        foreach (var root in _allRootNodes)
        {
            var include = root.ApplyFilter(FilterText);
            if (include)
            {
                _nodes.Add(root);
            }
        }
    }

    private ChapterNode? BuildGroupNode(ChapterGroupDefinition definition, ISet<long> assigned)
    {
        var node = new ChapterNode(definition.Id, definition.Title, isGroup: true, chapter: null);
        foreach (var chapterId in definition.ChapterIds)
        {
            if (!_chapterLookup.TryGetValue(chapterId, out var chapterNode))
            {
                continue;
            }

            if (!assigned.Add(chapterId))
            {
                continue;
            }

            node.AddChild(chapterNode);
        }

        foreach (var childGroup in definition.Groups)
        {
            var childNode = BuildGroupNode(childGroup, assigned);
            if (childNode is not null)
            {
                node.AddChild(childNode);
            }
        }

        if (node.AllChildren.Count == 0)
        {
            return null;
        }

        node.UpdateAggregateValidation();
        return node;
    }

    private ChapterNode? FindFirstChapterNode()
    {
        foreach (var root in _allRootNodes)
        {
            if (!root.IsGroup)
            {
                return root;
            }

            var descendant = root.EnumerateDescendants().FirstOrDefault(child => !child.IsGroup);
            if (descendant is not null)
            {
                return descendant;
            }
        }

        return null;
    }

    private static IEnumerable<ChapterGroupDefinition> ParseChapterGroups(Dictionary<string, JToken> metadata)
    {
        foreach (var (key, value) in metadata)
        {
            if (!key.StartsWith("chapter_groups", StringComparison.OrdinalIgnoreCase))
            {
                continue;
            }

            foreach (var definition in ParseGroupToken(value))
            {
                yield return definition;
            }
        }
    }

    private static IEnumerable<ChapterGroupDefinition> ParseGroupToken(JToken token)
    {
        if (token is JObject obj)
        {
            if (obj.TryGetValue("groups", out var nested))
            {
                foreach (var group in ParseGroupToken(nested))
                {
                    yield return group;
                }
            }

            var definition = ParseGroupDefinition(obj);
            if (definition is not null)
            {
                yield return definition;
            }
        }
        else if (token is JArray array)
        {
            foreach (var child in array)
            {
                foreach (var definition in ParseGroupToken(child))
                {
                    yield return definition;
                }
            }
        }
    }

    private static ChapterGroupDefinition? ParseGroupDefinition(JObject obj)
    {
        var id = obj.Value<string>("id") ?? Guid.NewGuid().ToString("N", CultureInfo.InvariantCulture);
        var title = obj.Value<string>("title") ?? id;

        var definition = new ChapterGroupDefinition(id, title);

        AppendChapterIds(definition, obj["chapters"]);
        AppendChapterIds(definition, obj["chapter_ids"]);
        AppendChapterIds(definition, obj["chapter"]);
        AppendChildren(definition, obj["children"]);

        if (obj.TryGetValue("groups", out var nestedGroups))
        {
            foreach (var group in ParseGroupToken(nestedGroups))
            {
                definition.Groups.Add(group);
            }
        }

        return definition.ChapterIds.Count == 0 && definition.Groups.Count == 0 ? null : definition;
    }

    private static void AppendChildren(ChapterGroupDefinition definition, JToken? token)
    {
        if (token is null)
        {
            return;
        }

        if (token is JArray array)
        {
            foreach (var item in array)
            {
                if (item is JObject childObj)
                {
                    var type = childObj.Value<string>("type")?.ToLowerInvariant();
                    switch (type)
                    {
                        case "chapter":
                            AppendChapterIds(definition, childObj["id"] ?? childObj["chapter"] ?? childObj["chapters"]);
                            break;
                        case "group":
                            var nested = childObj["group"] as JObject ?? childObj;
                            var parsed = ParseGroupDefinition(nested);
                            if (parsed is not null)
                            {
                                definition.Groups.Add(parsed);
                            }

                            break;
                        default:
                            AppendChapterIds(definition, childObj["id"] ?? childObj["chapter"] ?? childObj["chapters"]);
                            var nestedGroup = ParseGroupDefinition(childObj);
                            if (nestedGroup is not null)
                            {
                                definition.Groups.Add(nestedGroup);
                            }

                            break;
                    }
                }
                else
                {
                    AppendChapterIds(definition, item);
                }
            }
        }
        else
        {
            AppendChapterIds(definition, token);
        }
    }

    private static void AppendChapterIds(ChapterGroupDefinition definition, JToken? token)
    {
        if (token is null)
        {
            return;
        }

        if (token.Type == JTokenType.Array)
        {
            foreach (var element in token)
            {
                AppendChapterIds(definition, element);
            }
        }
        else if (token.Type == JTokenType.Integer || token.Type == JTokenType.Float)
        {
            definition.ChapterIds.Add(token.Value<long>());
        }
        else if (token.Type == JTokenType.String && long.TryParse(token.Value<string>(), NumberStyles.Integer, CultureInfo.InvariantCulture, out var parsed))
        {
            definition.ChapterIds.Add(parsed);
        }
    }

    private IEnumerable<ValidationIssue> RunValidators(FTBQuests.IO.QuestPack pack)
    {
        foreach (var validator in _validators)
        {
            foreach (var issue in validator.Validate(pack))
            {
                yield return issue;
            }
        }
    }

    private void ApplyValidationIssues(IEnumerable<ValidationIssue> issues)
    {
        if (_currentPack is null)
        {
            return;
        }

        var severityByChapter = new Dictionary<long, ValidationSeverity>();
        foreach (var issue in issues)
        {
            var chapterId = ResolveChapterId(issue.Path, _currentPack);
            if (chapterId is null)
            {
                continue;
            }

            if (!severityByChapter.TryGetValue(chapterId.Value, out var existing) || issue.Severity > existing)
            {
                severityByChapter[chapterId.Value] = issue.Severity;
            }
        }

        foreach (var (chapterId, severity) in severityByChapter)
        {
            if (_chapterLookup.TryGetValue(chapterId, out var node))
            {
                node.SetValidationSeverity(severity);
            }
        }
    }

    private static long? ResolveChapterId(string path, FTBQuests.IO.QuestPack pack)
    {
        if (string.IsNullOrEmpty(path))
        {
            return null;
        }

        var marker = "chapters[";
        var index = path.IndexOf(marker, StringComparison.Ordinal);
        if (index < 0)
        {
            return null;
        }

        var start = index + marker.Length;
        var end = path.IndexOf(']', start);
        if (end < 0)
        {
            return null;
        }

        if (!int.TryParse(path.AsSpan(start, end - start), NumberStyles.Integer, CultureInfo.InvariantCulture, out var chapterIndex))
        {
            return null;
        }

        if (chapterIndex < 0 || chapterIndex >= pack.Chapters.Count)
        {
            return null;
        }

        return pack.Chapters[chapterIndex]?.Id;
    }

    private ChapterNode CreateChapterNode(Chapter chapter, ValidationSeverity severity)
    {
        var node = new ChapterNode(chapter.Id.ToString(CultureInfo.InvariantCulture), chapter.Title, isGroup: false, chapter);
        node.SetValidationSeverity(severity);
        return node;
    }

    private sealed class ChapterGroupDefinition
    {
        public ChapterGroupDefinition(string id, string title)
        {
            Id = id;
            Title = title;
        }

        public string Id { get; }

        public string Title { get; }

        public List<long> ChapterIds { get; } = new();

        public List<ChapterGroupDefinition> Groups { get; } = new();
    }
}
