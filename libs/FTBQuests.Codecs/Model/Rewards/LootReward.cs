using FTBQuests.Core.Model;
using FTBQuests.Core.Enums;

namespace FTBQuests.Codecs.Model.Rewards
{
    public class LootReward : RewardBase
    {
        public string? LootId { get; set; }

        public LootReward(string id) : base(id, FTBQuests.Core.Enums.RewardType.Loot.ToString()) { }
    }
}







