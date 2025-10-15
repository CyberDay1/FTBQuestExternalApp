using FTBQuestExternalApp.Codecs.Enums;

namespace FTBQuestExternalApp.Codecs.Model;

public sealed class XpReward : RewardBase
{
    public XpReward()
        : base("xp", RewardType.Xp)
    {
    }

    public int Amount { get; set; }

    public bool Levels { get; set; }
}
