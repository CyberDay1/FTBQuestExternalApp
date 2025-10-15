using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Tasks;

public sealed class XpTaskPropertiesViewModel : TaskPropertiesViewModel
{
    private readonly XpTask task;
    private int amount;
    private string? amountLocalError;
    private string? amountValidationError;
    private string? amountIssue;
    private bool levels;
    private string? levelsValidationError;
    private string? levelsIssue;

    public XpTaskPropertiesViewModel(XpTask task, string pathPrefix)
        : base(task, pathPrefix, "Experience Task")
    {
        this.task = task;
        amount = task.Amount;
        levels = task.Levels;
        ValidateAmount(amount);
    }

    public int Amount
    {
        get => amount;
        set
        {
            if (SetProperty(ref amount, value))
            {
                ValidateAmount(value);
            }
        }
    }

    public string? AmountIssue
    {
        get => amountIssue;
        private set => SetProperty(ref amountIssue, value);
    }

    public bool Levels
    {
        get => levels;
        set
        {
            if (SetProperty(ref levels, value))
            {
                task.Levels = value;
                RefreshIssues();
            }
        }
    }

    public string? LevelsIssue
    {
        get => levelsIssue;
        private set => SetProperty(ref levelsIssue, value);
    }

    protected override void OnValidationIssuesChanged()
    {
        amountValidationError = GetIssueMessage("amount", "value");
        levelsValidationError = GetIssueMessage("levels", "is_levels");
        RefreshIssues();
    }

    private void ValidateAmount(int value)
    {
        if (value < 0)
        {
            amountLocalError = "Amount cannot be negative.";
        }
        else
        {
            task.Amount = value;
            amountLocalError = null;
        }

        RefreshIssues();
    }

    private void RefreshIssues()
    {
        AmountIssue = CombineMessages(amountLocalError, amountValidationError);
        LevelsIssue = CombineMessages(null, levelsValidationError);
    }
}
