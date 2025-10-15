using System.Collections.Generic;
using System.Linq;
using FTBQuestEditor.WinUI.ViewModels;
using FTBQuestExternalApp.Codecs.Model;
using FTBQuests.IO;
using Xunit;

namespace FTBQuests.Tests.Grid;

public class GridViewViewModelTests
{
    [Fact]
    public void DraggingIconSnapsToGrid()
    {
        var viewModel = CreateViewModelWithPack();
        var icon = viewModel.Icons.First(i => i.Id == 1);

        viewModel.BeginIconDrag(icon.Id, icon.X, icon.Y, new SelectionModifier(false, false));
        viewModel.UpdatePointer(icon.X + 40, icon.Y + 40);
        viewModel.CompleteInteraction();

        Assert.Equal(GridConstants.CellSize, icon.X);
        Assert.Equal(GridConstants.CellSize, icon.Y);
    }

    [Fact]
    public void SelectionRectangleCapturesIcons()
    {
        var viewModel = CreateViewModelWithPack();
        viewModel.BeginSelection(0, 0, additive: false);
        viewModel.UpdatePointer(GridConstants.CellSize * 2, GridConstants.CellSize * 2);
        viewModel.CompleteInteraction();

        var selectedIds = viewModel.SelectedIcons.Select(icon => icon.Id).OrderBy(id => id).ToArray();
        Assert.Equal(new[] { 1, 2 }, selectedIds);
    }

    [Fact]
    public void MoveSelectionByCellsUsesGridIncrement()
    {
        var viewModel = CreateViewModelWithPack();
        var icon = viewModel.Icons.First(i => i.Id == 2);

        viewModel.BeginIconDrag(icon.Id, icon.X, icon.Y, new SelectionModifier(false, false));
        viewModel.CancelInteraction();

        var startX = icon.X;
        var startY = icon.Y;

        viewModel.MoveSelectionByCells(1, -1);

        Assert.Equal(GridConstants.Snap(startX + GridConstants.CellSize), icon.X);
        Assert.Equal(GridConstants.Snap(startY - GridConstants.CellSize), icon.Y);
    }

    [Fact]
    public void ZoomAtIsClamped()
    {
        var viewModel = CreateViewModelWithPack();
        viewModel.ZoomAt(50, 0, 0);
        Assert.True(viewModel.Zoom <= 3.5d);

        viewModel.ZoomAt(-50, 0, 0);
        Assert.True(viewModel.Zoom >= 0.35d);
    }

    private static GridViewViewModel CreateViewModelWithPack()
    {
        var pack = new QuestPack();
        var chapter = new Chapter
        {
            Id = 1,
            Title = "Basics",
        };

        chapter.AddQuest(new Quest
        {
            Id = 1,
            Title = "First Quest",
            PositionX = 0,
            PositionY = 0,
        });

        chapter.AddQuest(new Quest
        {
            Id = 2,
            Title = "Second Quest",
            PositionX = 1,
            PositionY = 1,
        });

        pack.AddChapter(chapter);

        var viewModel = new GridViewViewModel();
        viewModel.LoadChapter(chapter);
        return viewModel;
    }
}
