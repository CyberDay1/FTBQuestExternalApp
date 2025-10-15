using FTBQuestEditor.WinUI.ViewModels;
using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Tasks;

public sealed class AdvancementTaskPropertiesViewModel : TaskPropertiesViewModel
{
    private readonly AdvancementTask task;
    private string advancementId;
    private string? advancementLocalError;
    private string? advancementValidationError;
    private string? advancementIssue;

    public AdvancementTaskPropertiesViewModel(AdvancementTask task, string pathPrefix)
        : base(task, pathPrefix, "Advancement Task")
    {
        this.task = task;
        advancementId = IdentifierFormatting.ToDisplayString(task.AdvancementId);
        ValidateAdvancementId(advancementId);
    }

    public string AdvancementId
    {
        get => advancementId;
        set
        {
            if (SetProperty(ref advancementId, value))
            {
                ValidateAdvancementId(value);
            }
        }
    }

    public string? AdvancementIssue
    {
        get => advancementIssue;
        private set => SetProperty(ref advancementIssue, value);
    }

    protected override void OnValidationIssuesChanged()
    {
        advancementValidationError = GetIssueMessage("advancement");
        RefreshIssues();
    }

    private void ValidateAdvancementId(string? value)
    {
        if (IdentifierFormatting.TryParse(value, out var identifier))
        {
            task.AdvancementId = identifier;
            advancementLocalError = null;
        }
        else
        {
            advancementLocalError = "Advancement identifier must be in namespace:path format.";
        }

        RefreshIssues();
    }

    private void RefreshIssues()
    {
        AdvancementIssue = CombineMessages(advancementLocalError, advancementValidationError);
    }
}
