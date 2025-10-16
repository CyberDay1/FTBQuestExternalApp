using FTBQuests.Validation;
using FTBQuests.Assets;
using FTBQuestExternalApp.Codecs.Enums;

namespace FTBQuestExternalApp.Codecs.Model;

public sealed class UnknownReward : RewardBase
{
    public UnknownReward(string typeId)
        : base(string.IsNullOrWhiteSpace(typeId) ? "custom" : typeId, RewardType.Custom)
    {
    }
}
