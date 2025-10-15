using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.Linq;
using System.Windows.Input;
using FTBQuestEditor.WinUI.ViewModels;
using FTBQuests.Validation;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Windows.Foundation;

namespace FTBQuestEditor.WinUI.Views;

public sealed partial class GraphOverlay : UserControl
{
    public static readonly DependencyProperty LinksProperty = DependencyProperty.Register(
        nameof(Links),
        typeof(ObservableCollection<Link>),
        typeof(GraphOverlay),
        new PropertyMetadata(null, OnLinksChanged));

    public static readonly DependencyProperty NodesProperty = DependencyProperty.Register(
        nameof(Nodes),
        typeof(IEnumerable<QuestNodeViewModel>),
        typeof(GraphOverlay),
        new PropertyMetadata(null, OnNodesChanged));

    public static readonly DependencyProperty TargetProperty = DependencyProperty.Register(
        nameof(Target),
        typeof(ListViewBase),
        typeof(GraphOverlay),
        new PropertyMetadata(null, OnTargetChanged));

    public static readonly DependencyProperty CreateLinkCommandProperty = DependencyProperty.Register(
        nameof(CreateLinkCommand),
        typeof(ICommand),
        typeof(GraphOverlay),
        new PropertyMetadata(null));

    public static readonly DependencyProperty DeleteLinkCommandProperty = DependencyProperty.Register(
        nameof(DeleteLinkCommand),
        typeof(ICommand),
        typeof(GraphOverlay),
        new PropertyMetadata(null));

    public static readonly DependencyProperty ValidationIssuesProperty = DependencyProperty.Register(
        nameof(ValidationIssues),
        typeof(IEnumerable<ValidationIssue>),
        typeof(GraphOverlay),
        new PropertyMetadata(null));

    private readonly Dictionary<Link, Path> linkVisuals = new();
    private readonly Dictionary<Guid, QuestNodeViewModel> nodeLookup = new();

    private ObservableCollection<Link>? subscribedLinks;
    private INotifyCollectionChanged? subscribedNodes;
    private Path? previewPath;
    private DragState? currentDrag;
    private readonly PointerEventHandler pointerPressedHandler;
    private readonly PointerEventHandler pointerMovedHandler;
    private readonly PointerEventHandler pointerReleasedHandler;
    private readonly PointerEventHandler pointerCanceledHandler;

    public GraphOverlay()
    {
        InitializeComponent();
        SizeChanged += (_, _) => UpdateAllLinkGeometries();
        pointerPressedHandler = OnTargetPointerPressed;
        pointerMovedHandler = OnTargetPointerMoved;
        pointerReleasedHandler = OnTargetPointerReleased;
        pointerCanceledHandler = OnTargetPointerCanceled;
    }

    public ObservableCollection<Link>? Links
    {
        get => (ObservableCollection<Link>?)GetValue(LinksProperty);
        set => SetValue(LinksProperty, value);
    }

    public IEnumerable<QuestNodeViewModel>? Nodes
    {
        get => (IEnumerable<QuestNodeViewModel>?)GetValue(NodesProperty);
        set => SetValue(NodesProperty, value);
    }

    public ListViewBase? Target
    {
        get => (ListViewBase?)GetValue(TargetProperty);
        set => SetValue(TargetProperty, value);
    }

    public ICommand? CreateLinkCommand
    {
        get => (ICommand?)GetValue(CreateLinkCommandProperty);
        set => SetValue(CreateLinkCommandProperty, value);
    }

    public ICommand? DeleteLinkCommand
    {
        get => (ICommand?)GetValue(DeleteLinkCommandProperty);
        set => SetValue(DeleteLinkCommandProperty, value);
    }

    public IEnumerable<ValidationIssue>? ValidationIssues
    {
        get => (IEnumerable<ValidationIssue>?)GetValue(ValidationIssuesProperty);
        set => SetValue(ValidationIssuesProperty, value);
    }

    public InfoBarSeverity MapSeverity(ValidationSeverity severity)
    {
        return severity switch
        {
            ValidationSeverity.Warning => InfoBarSeverity.Warning,
            ValidationSeverity.Error => InfoBarSeverity.Error,
            _ => InfoBarSeverity.Informational,
        };
    }

    private static void OnLinksChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
    {
        var overlay = (GraphOverlay)d;
        overlay.SubscribeToLinks(e.NewValue as ObservableCollection<Link>);
    }

    private static void OnNodesChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
    {
        var overlay = (GraphOverlay)d;
        overlay.SubscribeToNodes(e.NewValue as IEnumerable<QuestNodeViewModel>);
    }

    private static void OnTargetChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
    {
        var overlay = (GraphOverlay)d;
        overlay.OnTargetChanged(e.OldValue as ListViewBase, e.NewValue as ListViewBase);
    }

    private void SubscribeToLinks(ObservableCollection<Link>? newLinks)
    {
        if (subscribedLinks is not null)
        {
            subscribedLinks.CollectionChanged -= OnLinksCollectionChanged;
        }

        foreach (var path in linkVisuals.Values)
        {
            path.RightTapped -= OnLinkRightTapped;
        }

        LinkCanvas.Children.Clear();
        previewPath = null;
        linkVisuals.Clear();

        subscribedLinks = newLinks;
        if (subscribedLinks is not null)
        {
            subscribedLinks.CollectionChanged += OnLinksCollectionChanged;
            foreach (var link in subscribedLinks)
            {
                AddLinkVisual(link);
            }
        }

        UpdateAllLinkGeometries();
    }

    private void SubscribeToNodes(IEnumerable<QuestNodeViewModel>? newNodes)
    {
        if (subscribedNodes is not null)
        {
            subscribedNodes.CollectionChanged -= OnNodesCollectionChanged;
        }

        nodeLookup.Clear();

        if (newNodes is not null)
        {
            foreach (var node in newNodes)
            {
                nodeLookup[node.Id] = node;
            }

            if (newNodes is INotifyCollectionChanged observable)
            {
                subscribedNodes = observable;
                subscribedNodes.CollectionChanged += OnNodesCollectionChanged;
            }
        }

        UpdateAllLinkGeometries();
    }

    private void OnNodesCollectionChanged(object? sender, NotifyCollectionChangedEventArgs e)
    {
        if (Nodes is null)
        {
            nodeLookup.Clear();
        }
        else
        {
            nodeLookup.Clear();
            foreach (var node in Nodes)
            {
                nodeLookup[node.Id] = node;
            }
        }

        UpdateAllLinkGeometries();
    }

    private void OnLinksCollectionChanged(object? sender, NotifyCollectionChangedEventArgs e)
    {
        if (e.Action is NotifyCollectionChangedAction.Reset)
        {
            SubscribeToLinks(subscribedLinks);
            return;
        }

        if (e.OldItems is not null)
        {
            foreach (var item in e.OldItems.OfType<Link>())
            {
                RemoveLinkVisual(item);
            }
        }

        if (e.NewItems is not null)
        {
            foreach (var item in e.NewItems.OfType<Link>())
            {
                AddLinkVisual(item);
            }
        }

        UpdateAllLinkGeometries();
    }

    private void OnTargetChanged(ListViewBase? oldTarget, ListViewBase? newTarget)
    {
        if (oldTarget is not null)
        {
            oldTarget.LayoutUpdated -= OnTargetLayoutUpdated;
            oldTarget.RemoveHandler(UIElement.PointerPressedEvent, pointerPressedHandler);
            oldTarget.RemoveHandler(UIElement.PointerMovedEvent, pointerMovedHandler);
            oldTarget.RemoveHandler(UIElement.PointerReleasedEvent, pointerReleasedHandler);
            oldTarget.RemoveHandler(UIElement.PointerCanceledEvent, pointerCanceledHandler);
        }

        currentDrag = null;
        RemovePreviewPath();

        if (newTarget is not null)
        {
            newTarget.LayoutUpdated += OnTargetLayoutUpdated;
            newTarget.AddHandler(UIElement.PointerPressedEvent, pointerPressedHandler, true);
            newTarget.AddHandler(UIElement.PointerMovedEvent, pointerMovedHandler, true);
            newTarget.AddHandler(UIElement.PointerReleasedEvent, pointerReleasedHandler, true);
            newTarget.AddHandler(UIElement.PointerCanceledEvent, pointerCanceledHandler, true);
        }

        UpdateAllLinkGeometries();
    }

    private void OnTargetLayoutUpdated(object? sender, object e)
    {
        UpdateAllLinkGeometries();
    }

    private void AddLinkVisual(Link link)
    {
        if (linkVisuals.ContainsKey(link))
        {
            return;
        }

        var path = CreateLinkPath();
        path.Tag = link;
        path.RightTapped += OnLinkRightTapped;
        linkVisuals[link] = path;
        LinkCanvas.Children.Add(path);
    }

    private void RemoveLinkVisual(Link link)
    {
        if (!linkVisuals.TryGetValue(link, out var path))
        {
            return;
        }

        path.RightTapped -= OnLinkRightTapped;
        LinkCanvas.Children.Remove(path);
        linkVisuals.Remove(link);
    }

    private Path CreateLinkPath()
    {
        var accent = GetAccentColor();
        var path = new Path
        {
            StrokeThickness = 2,
            StrokeLineJoin = PenLineJoin.Round,
            StrokeStartLineCap = PenLineCap.Round,
            StrokeEndLineCap = PenLineCap.Round,
            Fill = new SolidColorBrush(accent),
            Stroke = new SolidColorBrush(accent),
            Visibility = Visibility.Collapsed,
        };

        return path;
    }

    private static Windows.UI.Color GetAccentColor()
    {
        if (Application.Current.Resources.TryGetValue("SystemAccentColor", out var accent) && accent is Windows.UI.Color color)
        {
            return color;
        }

        return Windows.UI.Colors.SteelBlue;
    }

    private void UpdateAllLinkGeometries()
    {
        foreach (var pair in linkVisuals.ToList())
        {
            UpdateLinkGeometry(pair.Key, pair.Value);
        }

        if (currentDrag is not null)
        {
            UpdatePreviewGeometry(currentDrag.PointerPosition);
        }
    }

    private void UpdateLinkGeometry(Link link, Path path)
    {
        if (Target is null)
        {
            path.Visibility = Visibility.Collapsed;
            return;
        }

        var sourceRect = GetContainerRect(link.SourceId);
        var targetRect = GetContainerRect(link.TargetId);

        if (sourceRect is null || targetRect is null)
        {
            path.Visibility = Visibility.Collapsed;
            return;
        }

        var geometry = BuildLinkGeometry(sourceRect.Value, targetRect.Value, includeArrow: true);
        path.Data = geometry;
        path.Visibility = Visibility.Visible;
    }

    private Rect? GetContainerRect(Guid questId)
    {
        if (Target is null || !nodeLookup.TryGetValue(questId, out var quest))
        {
            return null;
        }

        var container = Target.ContainerFromItem(quest) as FrameworkElement;
        if (container is null)
        {
            return null;
        }

        if (container.ActualWidth == 0 || container.ActualHeight == 0)
        {
            return null;
        }

        var transform = container.TransformToVisual(LinkCanvas);
        var topLeft = transform.TransformPoint(new Point(0, 0));
        var bottomRight = transform.TransformPoint(new Point(container.ActualWidth, container.ActualHeight));
        return new Rect(topLeft, bottomRight);
    }

    private static Geometry BuildLinkGeometry(Rect source, Rect target, bool includeArrow)
    {
        var points = CalculateRoute(source, target);
        if (points.Count < 2)
        {
            return Geometry.Empty;
        }

        var figure = new PathFigure
        {
            StartPoint = points[0],
            IsClosed = false,
            IsFilled = false,
        };

        var polyLine = new PolyLineSegment();
        for (var i = 1; i < points.Count; i++)
        {
            polyLine.Points.Add(points[i]);
        }

        figure.Segments.Add(polyLine);

        var geometry = new PathGeometry();
        geometry.Figures.Add(figure);

        if (includeArrow)
        {
            var end = points[^1];
            var previous = points[^2];
            var vector = new Point(end.X - previous.X, end.Y - previous.Y);
            var direction = Normalize(vector);
            var normal = new Point(-direction.Y, direction.X);
            const double arrowLength = 12;
            const double arrowWidth = 5;

            var arrowPoint1 = new Point(
                end.X - (direction.X * arrowLength) + (normal.X * arrowWidth),
                end.Y - (direction.Y * arrowLength) + (normal.Y * arrowWidth));
            var arrowPoint2 = new Point(
                end.X - (direction.X * arrowLength) - (normal.X * arrowWidth),
                end.Y - (direction.Y * arrowLength) - (normal.Y * arrowWidth));

            var arrowFigure = new PathFigure
            {
                StartPoint = end,
                IsClosed = true,
                IsFilled = true,
            };

            arrowFigure.Segments.Add(new LineSegment { Point = arrowPoint1 });
            arrowFigure.Segments.Add(new LineSegment { Point = arrowPoint2 });
            geometry.Figures.Add(arrowFigure);
        }

        return geometry;
    }

    private static List<Point> CalculateRoute(Rect source, Rect target)
    {
        var points = new List<Point>();
        var startEdge = DetermineStartEdge(source, target);
        var endEdge = DetermineEndEdge(source, target);
        var start = GetAnchorPoint(source, startEdge);
        var end = GetAnchorPoint(target, endEdge);

        points.Add(start);

        if (startEdge is Edge.Right && endEdge is Edge.Left)
        {
            var midX = (source.Right + target.Left) / 2;
            points.Add(new Point(midX, start.Y));
            points.Add(new Point(midX, end.Y));
        }
        else if (startEdge is Edge.Left && endEdge is Edge.Right)
        {
            var midX = (source.Left + target.Right) / 2;
            points.Add(new Point(midX, start.Y));
            points.Add(new Point(midX, end.Y));
        }
        else if (startEdge is Edge.Bottom && endEdge is Edge.Top)
        {
            var midY = (source.Bottom + target.Top) / 2;
            points.Add(new Point(start.X, midY));
            points.Add(new Point(end.X, midY));
        }
        else if (startEdge is Edge.Top && endEdge is Edge.Bottom)
        {
            var midY = (source.Top + target.Bottom) / 2;
            points.Add(new Point(start.X, midY));
            points.Add(new Point(end.X, midY));
        }
        else
        {
            points.Add(new Point(start.X, end.Y));
        }

        points.Add(end);
        return points;
    }

    private static Edge DetermineStartEdge(Rect source, Rect target)
    {
        if (source.Right <= target.Left)
        {
            return Edge.Right;
        }

        if (source.Left >= target.Right)
        {
            return Edge.Left;
        }

        if (source.Bottom <= target.Top)
        {
            return Edge.Bottom;
        }

        return Edge.Top;
    }

    private static Edge DetermineEndEdge(Rect source, Rect target)
    {
        if (source.Right <= target.Left)
        {
            return Edge.Left;
        }

        if (source.Left >= target.Right)
        {
            return Edge.Right;
        }

        if (source.Bottom <= target.Top)
        {
            return Edge.Top;
        }

        return Edge.Bottom;
    }

    private static Point GetAnchorPoint(Rect rect, Edge edge)
    {
        return edge switch
        {
            Edge.Left => new Point(rect.Left, rect.Top + rect.Height / 2),
            Edge.Right => new Point(rect.Right, rect.Top + rect.Height / 2),
            Edge.Top => new Point(rect.Left + rect.Width / 2, rect.Top),
            Edge.Bottom => new Point(rect.Left + rect.Width / 2, rect.Bottom),
            _ => rect.Center(),
        };
    }

    private static Point Normalize(Point vector)
    {
        var length = Math.Sqrt(vector.X * vector.X + vector.Y * vector.Y);
        if (length < 0.001)
        {
            return new Point(0, -1);
        }

        return new Point(vector.X / length, vector.Y / length);
    }

    private void OnLinkRightTapped(object sender, RightTappedRoutedEventArgs e)
    {
        if (sender is not Path path || path.Tag is not Link link)
        {
            return;
        }

        if (DeleteLinkCommand is null || !DeleteLinkCommand.CanExecute(link))
        {
            return;
        }

        var flyout = new MenuFlyout();
        var deleteItem = new MenuFlyoutItem { Text = "Delete link" };
        deleteItem.Click += (_, _) => DeleteLinkCommand.Execute(link);
        flyout.Items.Add(deleteItem);
        flyout.ShowAt(path, e.GetPosition(path));
        e.Handled = true;
    }

    private void OnTargetPointerPressed(object sender, PointerRoutedEventArgs e)
    {
        if (Target is null)
        {
            return;
        }

        var point = e.GetCurrentPoint(Target);
        if (!point.Properties.IsLeftButtonPressed)
        {
            return;
        }

        var quest = FindQuestFromPointer(e);
        if (quest is null)
        {
            return;
        }

        var pointerId = e.Pointer.PointerId;
        var sourceRect = GetContainerRect(quest.Id);
        if (sourceRect is null)
        {
            return;
        }

        Target.CapturePointer(e.Pointer);
        currentDrag = new DragState(quest, pointerId, point.Position);
        EnsurePreviewPath();
        UpdatePreviewGeometry(point.Position);
        e.Handled = true;
    }

    private void OnTargetPointerMoved(object sender, PointerRoutedEventArgs e)
    {
        if (Target is null || currentDrag is null || e.Pointer.PointerId != currentDrag.PointerId)
        {
            return;
        }

        var point = e.GetCurrentPoint(Target);
        currentDrag = currentDrag.Value with { PointerPosition = point.Position };
        UpdatePreviewGeometry(point.Position);
    }

    private void OnTargetPointerReleased(object sender, PointerRoutedEventArgs e)
    {
        if (Target is null || currentDrag is null || e.Pointer.PointerId != currentDrag.PointerId)
        {
            return;
        }

        var source = currentDrag.Value.Source;
        var point = e.GetCurrentPoint(Target);
        var quest = FindQuestFromPointer(e) ?? FindQuestAtPosition(point.Position);

        if (quest is not null && quest.Id != source.Id)
        {
            var link = new Link(source.Id, quest.Id);
            if (CreateLinkCommand?.CanExecute(link) == true)
            {
                CreateLinkCommand.Execute(link);
            }
        }

        CancelCurrentDrag(e.Pointer);
    }

    private void OnTargetPointerCanceled(object sender, PointerRoutedEventArgs e)
    {
        if (Target is null || currentDrag is null || e.Pointer.PointerId != currentDrag.PointerId)
        {
            return;
        }

        CancelCurrentDrag(e.Pointer);
    }

    private void CancelCurrentDrag(Pointer pointer)
    {
        Target?.ReleasePointerCapture(pointer);
        currentDrag = null;
        RemovePreviewPath();
    }

    private QuestNodeViewModel? FindQuestFromPointer(PointerRoutedEventArgs e)
    {
        if (Target is null)
        {
            return null;
        }

        if (e.OriginalSource is not DependencyObject source)
        {
            return null;
        }

        var container = FindAncestor<GridViewItem>(source) ?? FindAncestor<ListViewItem>(source);
        return container?.DataContext as QuestNodeViewModel;
    }

    private QuestNodeViewModel? FindQuestAtPosition(Point point)
    {
        if (Target is null)
        {
            return null;
        }

        var overlayPoint = TransformPoint(point, Target, LinkCanvas);
        foreach (var (id, quest) in nodeLookup)
        {
            var rect = GetContainerRect(id);
            if (rect is not null && rect.Value.Contains(overlayPoint))
            {
                return quest;
            }
        }

        return null;
    }

    private void EnsurePreviewPath()
    {
        if (previewPath is not null)
        {
            return;
        }

        previewPath = new Path
        {
            Stroke = new SolidColorBrush(Windows.UI.Colors.Gray),
            StrokeThickness = 1.5,
            StrokeDashArray = new DoubleCollection { 4, 2 },
            Visibility = Visibility.Visible,
        };

        LinkCanvas.Children.Add(previewPath);
    }

    private void RemovePreviewPath()
    {
        if (previewPath is null)
        {
            return;
        }

        LinkCanvas.Children.Remove(previewPath);
        previewPath = null;
    }

    private void UpdatePreviewGeometry(Point pointerPosition)
    {
        if (currentDrag is null || previewPath is null || Target is null)
        {
            return;
        }

        var sourceRect = GetContainerRect(currentDrag.Value.Source.Id);
        if (sourceRect is null)
        {
            previewPath.Visibility = Visibility.Collapsed;
            return;
        }

        var targetRect = new Rect(pointerPosition.X - 1, pointerPosition.Y - 1, 2, 2);
        var overlayTargetRect = TransformRect(targetRect, Target, LinkCanvas);
        var geometry = BuildLinkGeometry(sourceRect.Value, overlayTargetRect, includeArrow: false);
        previewPath.Data = geometry;
        previewPath.Visibility = Visibility.Visible;
    }

    private static Rect TransformRect(Rect rect, UIElement from, UIElement to)
    {
        var transform = from.TransformToVisual(to);
        var topLeft = transform.TransformPoint(new Point(rect.Left, rect.Top));
        var bottomRight = transform.TransformPoint(new Point(rect.Right, rect.Bottom));
        return new Rect(topLeft, bottomRight);
    }

    private static Point TransformPoint(Point point, UIElement from, UIElement to)
    {
        var transform = from.TransformToVisual(to);
        return transform.TransformPoint(point);
    }

    private static T? FindAncestor<T>(DependencyObject? current)
        where T : DependencyObject
    {
        while (current is not null)
        {
            if (current is T match)
            {
                return match;
            }

            current = VisualTreeHelper.GetParent(current);
        }

        return null;
    }

    private enum Edge
    {
        Left,
        Right,
        Top,
        Bottom,
    }

    private readonly record struct DragState(QuestNodeViewModel Source, uint PointerId, Point PointerPosition);
}

internal static class RectExtensions
{
    public static Point Center(this Rect rect)
    {
        return new Point(rect.Left + rect.Width / 2, rect.Top + rect.Height / 2);
    }
}
