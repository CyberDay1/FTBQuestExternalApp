using FTBQuests.Core.Model;

namespace FTBQuests.Codecs.Model.Rewards
{
    public class XpReward : RewardBase
    {
        public int Amount { get; set; }

        public XpReward(string id) : base(id, FTBQuests.Core.Model.RewardType.Xp.ToString()) { }
    }
}



