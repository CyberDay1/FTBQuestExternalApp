using FTBQuestExternalApp.Codecs.Enums;

namespace FTBQuestExternalApp.Codecs.Model;

public sealed class LootTableReward : RewardBase
{
    public LootTableReward()
        : base("loot_table", RewardType.LootTable)
    {
    }

    public string TableName { get; set; } = string.Empty;
}
