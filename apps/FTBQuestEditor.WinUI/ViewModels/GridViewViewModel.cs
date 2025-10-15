using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Globalization;
using System.Linq;
using CommunityToolkit.Mvvm.ComponentModel;
using FTBQuestExternalApp.Codecs.Model;
using FTBQuests.IO;

namespace FTBQuestEditor.WinUI.ViewModels;

/// <summary>
/// Represents the validation state badge rendered on top of a quest icon.
/// </summary>
public enum ValidationBadge
{
    None,
    Warning,
    Error,
}

/// <summary>
/// Represents an axis-aligned rectangle in grid space.
/// </summary>
public readonly record struct SelectionBox(double X, double Y, double Width, double Height, bool IsActive)
{
    public static SelectionBox Empty { get; } = new(0, 0, 0, 0, false);

    public bool HasArea => IsActive && Width > 0 && Height > 0;
}

/// <summary>
/// View model for a single quest icon rendered on the grid.
/// </summary>
public sealed partial class QuestIconViewModel : ObservableObject
{
    private double _x;
    private double _y;
    private bool _isSelected;

    public QuestIconViewModel(long id, string label, double x, double y, ValidationBadge badge)
    {
        Id = id;
        Label = label;
        _x = GridConstants.Snap(x);
        _y = GridConstants.Snap(y);
        Badge = badge;
    }

    public long Id { get; }

    public string Label { get; }

    public ValidationBadge Badge { get; }

    public double X
    {
        get => _x;
        set => SetProperty(ref _x, value);
    }

    public double Y
    {
        get => _y;
        set => SetProperty(ref _y, value);
    }

    public bool IsSelected
    {
        get => _isSelected;
        set => SetProperty(ref _isSelected, value);
    }

    public double Size => GridConstants.CellSize;

    public double IconScale => GridConstants.IconScale;

    public bool HasBadge => Badge != ValidationBadge.None;

    public string BadgeGlyph => Badge switch
    {
        ValidationBadge.Error => "!",
        ValidationBadge.Warning => "?",
        _ => string.Empty,
    };
}

/// <summary>
/// Encapsulates modifier keys used when interacting with the grid.
/// </summary>
public readonly record struct SelectionModifier(bool IsControlDown, bool IsShiftDown)
{
    public bool PreserveExisting => IsControlDown || IsShiftDown;
}

/// <summary>
/// Manages interactive state for the quest grid including zoom, pan, and selection.
/// </summary>
public sealed partial class GridViewViewModel : ObservableObject
{
    private const double MinZoom = 0.35d;
    private const double MaxZoom = 3.5d;

    private readonly Dictionary<long, QuestIconViewModel> _iconLookup;
    private readonly Dictionary<QuestIconViewModel, (double X, double Y)> _dragSnapshot = new();

    private double _zoom = 1d;
    private double _panX;
    private double _panY;
    private SelectionBox _selection = SelectionBox.Empty;
    private InteractionMode _interaction = InteractionMode.None;
    private bool _selectionAdditive;
    private double _interactionStartX;
    private double _interactionStartY;
    private double _panOriginX;
    private double _panOriginY;

    public GridViewViewModel()
    {
        Icons = new ObservableCollection<QuestIconViewModel>();
        _iconLookup = new Dictionary<long, QuestIconViewModel>();
    }

    public ObservableCollection<QuestIconViewModel> Icons { get; }

    public double Zoom
    {
        get => _zoom;
        set
        {
            var clamped = Math.Clamp(value, MinZoom, MaxZoom);
            SetProperty(ref _zoom, clamped);
        }
    }

    public double PanX
    {
        get => _panX;
        private set => SetProperty(ref _panX, value);
    }

    public double PanY
    {
        get => _panY;
        private set => SetProperty(ref _panY, value);
    }

    public SelectionBox Selection
    {
        get => _selection;
        private set => SetProperty(ref _selection, value);
    }

    private enum InteractionMode
    {
        None,
        Dragging,
        Panning,
        Selecting,
    }

    public IReadOnlyCollection<QuestIconViewModel> SelectedIcons => Icons.Where(icon => icon.IsSelected).ToList();

    public void LoadQuestPack(QuestPack pack)
    {
        ArgumentNullException.ThrowIfNull(pack);

        var firstChapter = pack.Chapters.FirstOrDefault(chapter => chapter is not null);
        LoadChapter(firstChapter);
    }

    public void LoadChapter(Chapter? chapter)
    {
        ClearSelection();

        Icons.Clear();
        _iconLookup.Clear();

        if (chapter?.Quests is null)
        {
            NotifySelectionChanged();
            return;
        }

        foreach (var quest in chapter.Quests)
        {
            if (quest is null)
            {
                continue;
            }

            var label = string.IsNullOrWhiteSpace(quest.Title)
                ? quest.Id.ToString(CultureInfo.InvariantCulture)
                : quest.Title;

            var icon = new QuestIconViewModel(
                quest.Id,
                label,
                quest.PositionX * GridConstants.CellSize,
                quest.PositionY * GridConstants.CellSize,
                ValidationBadge.None);

            Icons.Add(icon);
            _iconLookup[icon.Id] = icon;
        }

        NotifySelectionChanged();
    }

    private void NotifySelectionChanged()
    {
        OnPropertyChanged(nameof(SelectedIcons));
    }

    public void BeginIconDrag(long iconId, double pointerX, double pointerY, SelectionModifier modifier)
    {
        if (!_iconLookup.TryGetValue(iconId, out var icon))
        {
            return;
        }

        ApplySelectionForClick(icon, modifier);
        StartDrag(pointerX, pointerY);
    }

    public void BeginDragWithSelection(double pointerX, double pointerY)
    {
        if (!Icons.Any(icon => icon.IsSelected))
        {
            return;
        }

        StartDrag(pointerX, pointerY);
    }

    private void StartDrag(double pointerX, double pointerY)
    {
        _dragSnapshot.Clear();
        foreach (var selected in Icons.Where(icon => icon.IsSelected))
        {
            _dragSnapshot[selected] = (selected.X, selected.Y);
        }

        _interactionStartX = pointerX;
        _interactionStartY = pointerY;
        _interaction = InteractionMode.Dragging;
    }

    public void BeginPan(double pointerX, double pointerY)
    {
        _interaction = InteractionMode.Panning;
        _interactionStartX = pointerX;
        _interactionStartY = pointerY;
        _panOriginX = PanX;
        _panOriginY = PanY;
    }

    public void BeginSelection(double pointerX, double pointerY, bool additive)
    {
        _interaction = InteractionMode.Selecting;
        _interactionStartX = pointerX;
        _interactionStartY = pointerY;
        _selectionAdditive = additive;

        if (!additive)
        {
            ClearSelection();
        }

        Selection = new SelectionBox(pointerX, pointerY, 0, 0, true);
    }

    public void UpdatePointer(double pointerX, double pointerY)
    {
        switch (_interaction)
        {
            case InteractionMode.Dragging:
                UpdateDrag(pointerX, pointerY);
                break;
            case InteractionMode.Panning:
                UpdatePan(pointerX, pointerY);
                break;
            case InteractionMode.Selecting:
                UpdateSelection(pointerX, pointerY);
                break;
        }
    }

    public void CompleteInteraction()
    {
        if (_interaction == InteractionMode.Selecting)
        {
            FinalizeSelection();
        }

        _interaction = InteractionMode.None;
        _dragSnapshot.Clear();
        Selection = SelectionBox.Empty;
    }

    public void CancelInteraction()
    {
        if (_interaction == InteractionMode.Dragging)
        {
            foreach (var (icon, snapshot) in _dragSnapshot)
            {
                icon.X = snapshot.X;
                icon.Y = snapshot.Y;
            }
        }

        _interaction = InteractionMode.None;
        _dragSnapshot.Clear();
        Selection = SelectionBox.Empty;
    }

    public void MoveSelectionByCells(int deltaX, int deltaY)
    {
        if (deltaX == 0 && deltaY == 0)
        {
            return;
        }

        foreach (var icon in Icons.Where(icon => icon.IsSelected))
        {
            var targetX = icon.X + (deltaX * GridConstants.CellSize);
            var targetY = icon.Y + (deltaY * GridConstants.CellSize);
            icon.X = GridConstants.Snap(targetX);
            icon.Y = GridConstants.Snap(targetY);
        }
    }

    public void ZoomAt(double wheelDelta, double focusX, double focusY)
    {
        if (wheelDelta == 0)
        {
            return;
        }

        var zoomFactor = Math.Pow(1.1d, wheelDelta);
        var newZoom = Math.Clamp(Zoom * zoomFactor, MinZoom, MaxZoom);
        var scaleRatio = newZoom / Zoom;

        var offsetX = focusX - ((focusX - PanX) * scaleRatio);
        var offsetY = focusY - ((focusY - PanY) * scaleRatio);

        Zoom = newZoom;
        PanX = offsetX;
        PanY = offsetY;
    }

    public void PanBy(double deltaX, double deltaY)
    {
        PanX += deltaX;
        PanY += deltaY;
    }

    public void ClearSelection()
    {
        var changed = false;
        foreach (var icon in Icons.Where(icon => icon.IsSelected).ToList())
        {
            icon.IsSelected = false;
            changed = true;
        }

        if (changed)
        {
            NotifySelectionChanged();
        }
    }

    private void ApplySelectionForClick(QuestIconViewModel icon, SelectionModifier modifier)
    {
        var changed = false;
        if (modifier.IsControlDown)
        {
            icon.IsSelected = !icon.IsSelected;
            changed = true;
        }
        else if (modifier.IsShiftDown)
        {
            if (!icon.IsSelected)
            {
                icon.IsSelected = true;
                changed = true;
            }
        }
        else
        {
            if (!icon.IsSelected || Icons.Any(other => other.IsSelected && other != icon))
            {
                ClearSelection();
                icon.IsSelected = true;
                changed = true;
            }
        }

        if (changed)
        {
            NotifySelectionChanged();
        }
    }

    private void UpdateDrag(double pointerX, double pointerY)
    {
        if (_dragSnapshot.Count == 0)
        {
            return;
        }

        var deltaX = pointerX - _interactionStartX;
        var deltaY = pointerY - _interactionStartY;

        foreach (var (icon, snapshot) in _dragSnapshot)
        {
            var targetX = snapshot.X + deltaX;
            var targetY = snapshot.Y + deltaY;
            icon.X = GridConstants.Snap(targetX);
            icon.Y = GridConstants.Snap(targetY);
        }
    }

    private void UpdatePan(double pointerX, double pointerY)
    {
        var deltaX = pointerX - _interactionStartX;
        var deltaY = pointerY - _interactionStartY;
        PanX = _panOriginX + deltaX;
        PanY = _panOriginY + deltaY;
    }

    private void UpdateSelection(double pointerX, double pointerY)
    {
        var x1 = Math.Min(pointerX, _interactionStartX);
        var y1 = Math.Min(pointerY, _interactionStartY);
        var x2 = Math.Max(pointerX, _interactionStartX);
        var y2 = Math.Max(pointerY, _interactionStartY);
        Selection = new SelectionBox(x1, y1, x2 - x1, y2 - y1, true);
    }

    private void FinalizeSelection()
    {
        if (!Selection.HasArea)
        {
            return;
        }

        var changed = false;
        foreach (var icon in Icons)
        {
            var iconRight = icon.X + GridConstants.CellSize;
            var iconBottom = icon.Y + GridConstants.CellSize;
            var within = icon.X >= Selection.X && icon.Y >= Selection.Y && iconRight <= Selection.X + Selection.Width && iconBottom <= Selection.Y + Selection.Height;

            if (within)
            {
                if (!icon.IsSelected)
                {
                    icon.IsSelected = true;
                    changed = true;
                }
            }
            else if (!_selectionAdditive && icon.IsSelected)
            {
                icon.IsSelected = false;
                changed = true;
            }
        }

        if (changed)
        {
            NotifySelectionChanged();
        }
    }
}
