using FTBQuests.Core.Model;

namespace FTBQuests.Codecs.Model.Rewards
{
    public class CustomReward : RewardBase
    {
        public string? CustomId { get; set; }

        public CustomReward(string id) : base(id, RewardType.Custom.ToString()) { }
    }
}


