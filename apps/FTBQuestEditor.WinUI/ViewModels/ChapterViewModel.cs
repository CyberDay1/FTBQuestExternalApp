using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="ChapterViewModel.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Linq;
using CommunityToolkit.Mvvm.ComponentModel;
using FTBQuests.Codecs.Model;
using FTBQuests.IO;

namespace FTBQuestEditor.WinUI.ViewModels;

/// <summary>
/// Maintains the currently active chapter context and coordinates updates to the grid surface.
/// </summary>
public sealed class ChapterViewModel : ObservableObject
{
    private readonly GridViewViewModel _gridViewModel;
    private FTBQuests.IO.QuestPack? _pack;
    private Chapter? _currentChapter;

    public ChapterViewModel(GridViewViewModel gridViewModel)
    {
        _gridViewModel = gridViewModel;
    }

    public FTBQuests.IO.QuestPack? Pack
    {
        get => _pack;
        private set => SetProperty(ref _pack, value);
    }

    public Chapter? CurrentChapter
    {
        get => _currentChapter;
        private set => SetProperty(ref _currentChapter, value);
    }

    public GridViewViewModel Grid => _gridViewModel;

    public void LoadQuestPack(FTBQuests.IO.QuestPack pack)
    {
        ArgumentNullException.ThrowIfNull(pack);

        Pack = pack;
        var firstChapter = pack.Chapters.FirstOrDefault(chapter => chapter is not null);
        SetActiveChapter(firstChapter);
    }

    public void Clear()
    {
        Pack = null;
        SetActiveChapter(null);
    }

    public void SetActiveChapter(Chapter? chapter)
    {
        CurrentChapter = chapter;
        _gridViewModel.LoadChapter(chapter);
    }
}

