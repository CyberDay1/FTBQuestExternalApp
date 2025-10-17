using FTBQuests.Core.Model;
using FTBQuests.Core.Enums;

namespace FTBQuests.Codecs.Model.Rewards
{
    public class CustomReward : RewardBase
    {
        public string? CustomId { get; set; }

        public CustomReward(string id) : base(id, FTBQuests.Core.Enums.RewardType.Custom.ToString()) { }
    }
}







