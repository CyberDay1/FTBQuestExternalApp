using FTBQuests.Validation;
using FTBQuests.Assets;
using FTBQuests.Codecs.Enums;

namespace FTBQuests.Codecs.Model;

public sealed class LootTableReward : RewardBase
{
    public LootTableReward()
        : base("loot_table", RewardType.LootTable)
    {
    }

    public string TableName { get; set; } = string.Empty;
}

