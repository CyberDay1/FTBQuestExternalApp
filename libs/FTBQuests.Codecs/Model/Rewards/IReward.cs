using FTBQuestExternalApp.Codecs.Enums;

namespace FTBQuestExternalApp.Codecs.Model;

public interface IReward : IExtraAware
{
    RewardType RewardType { get; }

    string TypeId { get; }
}
