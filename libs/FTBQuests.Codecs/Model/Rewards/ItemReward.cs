using FTBQuests.Core.Model;
using FTBQuests.Core.Enums;

namespace FTBQuests.Codecs.Model.Rewards
{
    public class ItemReward : RewardBase
    {
        public string? ItemId { get; set; }
        public int Count { get; set; }

        public ItemReward(string id) : base(id, FTBQuests.Core.Enums.RewardType.Item.ToString()) { }
    }
}







