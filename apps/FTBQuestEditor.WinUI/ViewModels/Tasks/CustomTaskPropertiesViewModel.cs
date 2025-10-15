using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Tasks;

public sealed class CustomTaskPropertiesViewModel : TaskPropertiesViewModel
{
    public CustomTaskPropertiesViewModel(CustomTask task, string pathPrefix)
        : base(task, pathPrefix, "Custom Task")
    {
    }

    public string Description => "Custom tasks expose additional JSON-defined fields.";

    protected override void OnValidationIssuesChanged()
    {
        // Custom tasks do not have strongly typed fields.
    }
}
