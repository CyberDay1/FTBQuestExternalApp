using FTBQuests.Validation;
using FTBQuests.Assets;
using FTBQuestExternalApp.Codecs.Enums;

namespace FTBQuestExternalApp.Codecs.Model;

public sealed class CustomReward : RewardBase
{
    public CustomReward()
        : base("custom", RewardType.Custom)
    {
    }
}
