using FTBQuests.Validation;
using FTBQuests.Assets;
using FTBQuests.Codecs.Enums;

namespace FTBQuests.Codecs.Model;

public sealed class CustomReward : RewardBase
{
    public CustomReward()
        : base("custom", RewardType.Custom)
    {
    }
}

