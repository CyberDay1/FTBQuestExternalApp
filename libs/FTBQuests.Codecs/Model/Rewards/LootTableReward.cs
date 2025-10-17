using FTBQuests.Core.Model;

namespace FTBQuests.Codecs.Model.Rewards
{
    public class LootTableReward : RewardBase
    {
        public string? TableId { get; set; }

        public LootTableReward(string id) : base(id, RewardType.LootTable.ToString()) { }
    }
}


