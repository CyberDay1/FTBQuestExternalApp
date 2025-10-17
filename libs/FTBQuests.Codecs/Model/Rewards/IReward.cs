using FTBQuests.Core.Enums;
using FTBQuests.Core.Model;

namespace FTBQuests.Codecs.Model.Rewards
{
    public interface IReward
    {
        FTBQuests.Core.Enums.RewardType Type { get; }
    }
}
