using FTBQuests.Core.Model;
using FTBQuests.Core.Enums;

namespace FTBQuests.Codecs.Model.Rewards
{
    public class LootTableReward : RewardBase
    {
        public string? TableId { get; set; }

        public LootTableReward(string id) : base(id, FTBQuests.Core.Enums.RewardType.LootTable.ToString()) { }
    }
}







