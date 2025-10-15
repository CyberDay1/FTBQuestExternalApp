using System;
using System.Collections.Specialized;
using System.ComponentModel;
using System.Linq;
using FTBQuestEditor.WinUI.ViewModels;
using FTBQuests.IO;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace FTBQuestEditor.WinUI.Views;

public sealed partial class ChapterNavigator : UserControl
{
    public ChapterNavigator()
    {
        InitializeComponent();

        ListViewModel = new ChapterListViewModel();
        ChapterViewModel = new ChapterViewModel(GridView.ViewModel);

        ListViewModel.ChapterSelected += OnChapterSelected;
        ListViewModel.PropertyChanged += OnListPropertyChanged;
        ((INotifyCollectionChanged)ListViewModel.Nodes).CollectionChanged += OnNodesChanged;

        DataContext = this;
    }

    public ChapterListViewModel ListViewModel { get; }

    public ChapterViewModel ChapterViewModel { get; }

    public void LoadQuestPack(QuestPack pack)
    {
        ChapterViewModel.LoadQuestPack(pack);
        ListViewModel.LoadQuestPack(pack);
        RestoreExpansionAsync();
        EnsureSelectionAsync();
    }

    public void Clear()
    {
        ChapterViewModel.Clear();
        ListViewModel.Clear();
        ChapterTree.SelectedNodes.Clear();
    }

    private void OnChapterSelected(object? sender, FTBQuestExternalApp.Codecs.Model.Chapter? chapter)
    {
        ChapterViewModel.SetActiveChapter(chapter);
        EnsureSelectionAsync();
    }

    private void OnListPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == nameof(ChapterListViewModel.FilterText))
        {
            RestoreExpansionAsync();
            EnsureSelectionAsync();
        }
        else if (e.PropertyName == nameof(ChapterListViewModel.SelectedNode))
        {
            EnsureSelectionAsync();
        }
    }

    private void OnNodesChanged(object? sender, NotifyCollectionChangedEventArgs e)
    {
        RestoreExpansionAsync();
        EnsureSelectionAsync();
    }

    private void OnTreeViewItemInvoked(TreeView sender, TreeViewItemInvokedEventArgs args)
    {
        if (args.InvokedItem is ChapterNode node)
        {
            if (ListViewModel.SelectChapterCommand.CanExecute(node))
            {
                ListViewModel.SelectChapterCommand.Execute(node);
            }
        }
    }

    private void OnTreeViewSelectionChanged(TreeView sender, TreeViewSelectionChangedEventArgs args)
    {
        var node = args.AddedItems.OfType<ChapterNode>().FirstOrDefault();
        if (node is null)
        {
            if (args.RemovedItems.Count > 0 && ListViewModel.SelectChapterCommand.CanExecute(null))
            {
                ListViewModel.SelectChapterCommand.Execute(null);
            }

            return;
        }

        if (ListViewModel.SelectChapterCommand.CanExecute(node))
        {
            ListViewModel.SelectChapterCommand.Execute(node);
        }
    }

    private void OnTreeViewExpanding(TreeView sender, TreeViewExpandingEventArgs args)
    {
        if (args.Item is ChapterNode node)
        {
            node.IsExpanded = true;
        }
    }

    private void OnTreeViewCollapsed(TreeView sender, TreeViewCollapsedEventArgs args)
    {
        if (args.Item is ChapterNode node)
        {
            node.IsExpanded = false;
        }
    }

    private void RestoreExpansionAsync()
    {
        DispatcherQueue?.TryEnqueue(() =>
        {
            foreach (var root in ChapterTree.RootNodes)
            {
                ApplyExpansion(root);
            }
        });
    }

    private void EnsureSelectionAsync()
    {
        DispatcherQueue?.TryEnqueue(() =>
        {
            if (ListViewModel.SelectedNode is null)
            {
                ChapterTree.SelectedNodes.Clear();
                return;
            }

            if (TryFindNode(ListViewModel.SelectedNode, out var treeNode))
            {
                if (!ChapterTree.SelectedNodes.Contains(treeNode))
                {
                    ChapterTree.SelectedNodes.Clear();
                    ChapterTree.SelectedNodes.Add(treeNode);
                }

                ExpandAncestors(treeNode);
            }
        });
    }

    private void ApplyExpansion(TreeViewNode node)
    {
        if (node.Content is ChapterNode chapterNode)
        {
            node.IsExpanded = chapterNode.IsExpanded;
        }

        foreach (var child in node.Children)
        {
            ApplyExpansion(child);
        }
    }

    private bool TryFindNode(ChapterNode target, out TreeViewNode found)
    {
        foreach (var root in ChapterTree.RootNodes)
        {
            if (TryFindNodeRecursive(root, target, out found))
            {
                return true;
            }
        }

        found = null!;
        return false;
    }

    private bool TryFindNodeRecursive(TreeViewNode node, ChapterNode target, out TreeViewNode found)
    {
        if (ReferenceEquals(node.Content, target))
        {
            found = node;
            return true;
        }

        foreach (var child in node.Children)
        {
            if (TryFindNodeRecursive(child, target, out found))
            {
                return true;
            }
        }

        found = null!;
        return false;
    }

    private void ExpandAncestors(TreeViewNode node)
    {
        var current = node.Parent;
        while (current is not null)
        {
            current.IsExpanded = true;
            current = current.Parent;
        }
    }
}
