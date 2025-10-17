using FTBQuests.Core.Model;
using FTBQuests.Core.Enums;

namespace FTBQuests.Codecs.Model.Rewards
{
    public class UnknownReward : RewardBase
    {
        public UnknownReward(string id) : base(id, FTBQuests.Core.Enums.RewardType.Unknown.ToString()) { }
    }
}







