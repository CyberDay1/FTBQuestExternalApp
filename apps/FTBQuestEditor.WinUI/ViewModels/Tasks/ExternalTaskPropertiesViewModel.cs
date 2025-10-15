using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Tasks;

public sealed class ExternalTaskPropertiesViewModel : TaskPropertiesViewModel
{
    public ExternalTaskPropertiesViewModel(ITask task, string pathPrefix)
        : base(task, pathPrefix, $"External Task ({task.TypeId})")
    {
    }

    public string Description => "This task originates from an external plugin and cannot be edited here.";

    protected override void OnValidationIssuesChanged()
    {
        // External tasks are opaque to the editor.
    }
}
