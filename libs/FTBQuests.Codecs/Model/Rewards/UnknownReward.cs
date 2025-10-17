using FTBQuests.Codecs.Enums;

namespace FTBQuests.Codecs.Model;

public sealed class UnknownReward : RewardBase
{
    public UnknownReward(string typeId)
        : base(string.IsNullOrWhiteSpace(typeId) ? "custom" : typeId, RewardType.Custom)
    {
    }
}
