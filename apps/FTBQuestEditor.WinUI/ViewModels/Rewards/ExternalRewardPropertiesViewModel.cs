using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Rewards;

public sealed class ExternalRewardPropertiesViewModel : RewardPropertiesViewModel
{
    public ExternalRewardPropertiesViewModel(IReward reward, string pathPrefix)
        : base(reward, pathPrefix, $"External Reward ({reward.TypeId})")
    {
    }

    public string Description => "This reward comes from an external plugin and cannot be edited here.";

    protected override void OnValidationIssuesChanged()
    {
    }
}
