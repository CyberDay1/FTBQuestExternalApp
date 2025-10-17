using FTBQuests.Core.Model;

namespace FTBQuests.Codecs.Model.Rewards
{
    public class UnknownReward : RewardBase
    {
        public UnknownReward(string id) : base(id, RewardType.Unknown.ToString()) { }
    }
}


