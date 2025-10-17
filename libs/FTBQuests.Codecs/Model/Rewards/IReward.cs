using FTBQuests.Core.Model;

namespace FTBQuests.Codecs.Model.Rewards
{
    public interface IReward
    {
        RewardType Type { get; }
    }
}
