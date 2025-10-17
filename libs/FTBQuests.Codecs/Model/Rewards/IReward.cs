using FTBQuests.Codecs.Enums;

namespace FTBQuests.Codecs.Model;

public interface IReward : IExtraAware
{
    RewardType RewardType { get; }

    string TypeId { get; }
}
