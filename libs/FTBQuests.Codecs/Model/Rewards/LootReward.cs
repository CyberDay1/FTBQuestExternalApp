using FTBQuestExternalApp.Codecs.Enums;

namespace FTBQuestExternalApp.Codecs.Model;

public sealed class LootReward : RewardBase
{
    public LootReward()
        : base("loot", RewardType.Loot)
    {
    }

    public Identifier LootTable { get; set; }
}
