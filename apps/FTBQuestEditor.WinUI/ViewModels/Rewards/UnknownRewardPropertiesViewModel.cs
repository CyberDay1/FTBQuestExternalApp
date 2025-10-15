using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Rewards;

public sealed class UnknownRewardPropertiesViewModel : RewardPropertiesViewModel
{
    public UnknownRewardPropertiesViewModel(UnknownReward reward, string pathPrefix)
        : base(reward, pathPrefix, $"Unknown Reward ({reward.TypeId})")
    {
    }

    public string Description => "This reward type is not recognized. Editing is limited to raw JSON.";

    protected override void OnValidationIssuesChanged()
    {
    }
}
