using FTBQuests.Core.Model;

namespace FTBQuests.Codecs.Model.Rewards
{
    public class ItemReward : RewardBase
    {
        public string? ItemId { get; set; }
        public int Count { get; set; }

        public ItemReward(string id) : base(id, RewardType.Item.ToString()) { }
    }
}


