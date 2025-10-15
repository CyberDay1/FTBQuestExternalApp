using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Tasks;

public sealed class LocationTaskPropertiesViewModel : TaskPropertiesViewModel
{
    private readonly LocationTask task;
    private int x;
    private int y;
    private int z;
    private string? dimension;
    private int radius;
    private string? radiusLocalError;
    private string? radiusValidationError;
    private string? radiusIssue;
    private string? dimensionValidationError;
    private string? dimensionIssue;

    public LocationTaskPropertiesViewModel(LocationTask task, string pathPrefix)
        : base(task, pathPrefix, "Location Task")
    {
        this.task = task;
        x = task.X;
        y = task.Y;
        z = task.Z;
        dimension = task.Dimension;
        radius = task.Radius;
        ValidateRadius(radius);
    }

    public int X
    {
        get => x;
        set
        {
            if (SetProperty(ref x, value))
            {
                task.X = value;
                RefreshIssues();
            }
        }
    }

    public int Y
    {
        get => y;
        set
        {
            if (SetProperty(ref y, value))
            {
                task.Y = value;
                RefreshIssues();
            }
        }
    }

    public int Z
    {
        get => z;
        set
        {
            if (SetProperty(ref z, value))
            {
                task.Z = value;
                RefreshIssues();
            }
        }
    }

    public string? Dimension
    {
        get => dimension;
        set
        {
            if (SetProperty(ref dimension, value))
            {
                task.Dimension = string.IsNullOrWhiteSpace(value) ? null : value;
                RefreshIssues();
            }
        }
    }

    public string? DimensionIssue
    {
        get => dimensionIssue;
        private set => SetProperty(ref dimensionIssue, value);
    }

    public int Radius
    {
        get => radius;
        set
        {
            if (SetProperty(ref radius, value))
            {
                ValidateRadius(value);
            }
        }
    }

    public string? RadiusIssue
    {
        get => radiusIssue;
        private set => SetProperty(ref radiusIssue, value);
    }

    protected override void OnValidationIssuesChanged()
    {
        dimensionValidationError = GetIssueMessage("dimension", "dim");
        radiusValidationError = GetIssueMessage("radius");
        RefreshIssues();
    }

    private void ValidateRadius(int value)
    {
        if (value < 0)
        {
            radiusLocalError = "Radius cannot be negative.";
        }
        else
        {
            task.Radius = value;
            radiusLocalError = null;
        }

        RefreshIssues();
    }

    private void RefreshIssues()
    {
        DimensionIssue = CombineMessages(null, dimensionValidationError);
        RadiusIssue = CombineMessages(radiusLocalError, radiusValidationError);
    }
}
