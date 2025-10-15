using FTBQuestEditor.WinUI.ViewModels;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Windows.Foundation;
using Windows.System;

namespace FTBQuestEditor.WinUI.Views;

public sealed partial class GridViewPage : Page
{
    private enum PointerInteraction
    {
        None,
        Dragging,
        Selecting,
        Panning,
    }

    private PointerInteraction _interaction = PointerInteraction.None;

    public GridViewPage()
    {
        InitializeComponent();
        ViewModel = new GridViewViewModel();
        DataContext = ViewModel;
        Loaded += OnLoaded;
    }

    public GridViewViewModel ViewModel { get; }

    private void OnLoaded(object sender, RoutedEventArgs e)
    {
        CanvasRoot.Focus(FocusState.Programmatic);
    }

    private void Icon_PointerPressed(object sender, PointerRoutedEventArgs e)
    {
        if (sender is FrameworkElement element && element.DataContext is QuestIconViewModel icon)
        {
            element.CapturePointer(e.Pointer);
            var world = GetWorldPosition(e);
            var modifier = BuildModifier(e.KeyModifiers);
            ViewModel.BeginIconDrag(icon.Id, world.X, world.Y, modifier);
            _interaction = PointerInteraction.Dragging;
            e.Handled = true;
        }
    }

    private void Icon_PointerReleased(object sender, PointerRoutedEventArgs e)
    {
        if (_interaction == PointerInteraction.Dragging)
        {
            CompleteInteraction();
        }

        if (sender is FrameworkElement element)
        {
            element.ReleasePointerCapture(e.Pointer);
        }

        e.Handled = true;
    }

    private void Icon_PointerCanceled(object sender, PointerRoutedEventArgs e)
    {
        if (_interaction == PointerInteraction.Dragging)
        {
            ViewModel.CancelInteraction();
            _interaction = PointerInteraction.None;
        }

        if (sender is FrameworkElement element)
        {
            element.ReleasePointerCapture(e.Pointer);
        }

        e.Handled = true;
    }

    private void Canvas_PointerPressed(object sender, PointerRoutedEventArgs e)
    {
        var point = e.GetCurrentPoint(CanvasRoot);
        if (point.Properties.IsRightButtonPressed || point.Properties.IsMiddleButtonPressed)
        {
            CanvasRoot.CapturePointer(e.Pointer);
            ViewModel.BeginPan(point.Position.X, point.Position.Y);
            _interaction = PointerInteraction.Panning;
            e.Handled = true;
            return;
        }

        if (point.Properties.IsLeftButtonPressed)
        {
            CanvasRoot.CapturePointer(e.Pointer);
            var world = ScreenToWorld(point.Position);
            var additive = e.KeyModifiers.HasFlag(VirtualKeyModifiers.Control) || e.KeyModifiers.HasFlag(VirtualKeyModifiers.Shift);
            ViewModel.BeginSelection(world.X, world.Y, additive);
            _interaction = PointerInteraction.Selecting;
            e.Handled = true;
        }
    }

    private void Canvas_PointerMoved(object sender, PointerRoutedEventArgs e)
    {
        if (_interaction == PointerInteraction.None)
        {
            return;
        }

        switch (_interaction)
        {
            case PointerInteraction.Dragging:
            case PointerInteraction.Selecting:
            {
                var world = GetWorldPosition(e);
                ViewModel.UpdatePointer(world.X, world.Y);
                break;
            }
            case PointerInteraction.Panning:
            {
                var position = e.GetCurrentPoint(CanvasRoot).Position;
                ViewModel.UpdatePointer(position.X, position.Y);
                break;
            }
        }
    }

    private void Canvas_PointerReleased(object sender, PointerRoutedEventArgs e)
    {
        if (_interaction == PointerInteraction.None)
        {
            return;
        }

        CompleteInteraction();
        CanvasRoot.ReleasePointerCapture(e.Pointer);
        e.Handled = true;
    }

    private void Canvas_PointerCanceled(object sender, PointerRoutedEventArgs e)
    {
        ViewModel.CancelInteraction();
        _interaction = PointerInteraction.None;
        CanvasRoot.ReleasePointerCapture(e.Pointer);
        e.Handled = true;
    }

    private void Canvas_PointerWheelChanged(object sender, PointerRoutedEventArgs e)
    {
        var point = e.GetCurrentPoint(CanvasRoot);
        var world = ScreenToWorld(point.Position);
        var delta = point.Properties.MouseWheelDelta / 120.0;
        ViewModel.ZoomAt(delta, world.X, world.Y);
        e.Handled = true;
    }

    private void Canvas_KeyDown(object sender, KeyRoutedEventArgs e)
    {
        var dx = 0;
        var dy = 0;
        switch (e.Key)
        {
            case VirtualKey.Left:
                dx = -1;
                break;
            case VirtualKey.Right:
                dx = 1;
                break;
            case VirtualKey.Up:
                dy = -1;
                break;
            case VirtualKey.Down:
                dy = 1;
                break;
            default:
                return;
        }

        ViewModel.MoveSelectionByCells(dx, dy);
        e.Handled = true;
    }

    private void CompleteInteraction()
    {
        ViewModel.CompleteInteraction();
        _interaction = PointerInteraction.None;
    }

    private SelectionModifier BuildModifier(VirtualKeyModifiers modifiers)
    {
        var control = modifiers.HasFlag(VirtualKeyModifiers.Control);
        var shift = modifiers.HasFlag(VirtualKeyModifiers.Shift);
        return new SelectionModifier(control, shift);
    }

    private (double X, double Y) GetWorldPosition(PointerRoutedEventArgs e)
    {
        var point = e.GetCurrentPoint(CanvasRoot).Position;
        return ScreenToWorld(point);
    }

    private (double X, double Y) ScreenToWorld(Point position)
    {
        var zoom = ViewModel.Zoom;
        var worldX = (position.X - ViewModel.PanX) / zoom;
        var worldY = (position.Y - ViewModel.PanY) / zoom;
        return (worldX, worldY);
    }
}
