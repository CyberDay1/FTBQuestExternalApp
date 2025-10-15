using System.Linq;
using FTBQuestEditor.WinUI.ViewModels;
using Xunit;

namespace FTBQuests.Tests.Grid;

public class GridViewViewModelTests
{
    [Fact]
    public void DraggingIconSnapsToGrid()
    {
        var viewModel = new GridViewViewModel();
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
        var viewModel = new GridViewViewModel();
        viewModel.BeginSelection(0, 0, additive: false);
        viewModel.UpdatePointer(GridConstants.CellWithSpacing * 2, GridConstants.CellWithSpacing * 2);
        viewModel.CompleteInteraction();

        var selectedIds = viewModel.SelectedIcons.Select(icon => icon.Id).OrderBy(id => id).ToArray();
        Assert.Equal(new[] { 1, 2 }, selectedIds);
    }

    [Fact]
    public void MoveSelectionByCellsUsesGridIncrement()
    {
        var viewModel = new GridViewViewModel();
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
        var viewModel = new GridViewViewModel();
        viewModel.ZoomAt(50, 0, 0);
        Assert.True(viewModel.Zoom <= 3.5d);

        viewModel.ZoomAt(-50, 0, 0);
        Assert.True(viewModel.Zoom >= 0.35d);
    }
}
