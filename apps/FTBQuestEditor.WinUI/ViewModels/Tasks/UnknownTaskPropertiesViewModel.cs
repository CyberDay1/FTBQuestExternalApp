using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Tasks;

public sealed class UnknownTaskPropertiesViewModel : TaskPropertiesViewModel
{
    public UnknownTaskPropertiesViewModel(UnknownTask task, string pathPrefix)
        : base(task, pathPrefix, $"Unknown Task ({task.TypeId})")
    {
    }

    public string Description => "This task type is not recognized. Editing is limited to raw JSON.";

    protected override void OnValidationIssuesChanged()
    {
        // No strongly typed fields to validate.
    }
}
