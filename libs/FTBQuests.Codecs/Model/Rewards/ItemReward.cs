using FTBQuests.Validation;
using FTBQuests.Assets;
using FTBQuests.Codecs.Enums;

namespace FTBQuests.Codecs.Model;

public sealed class ItemReward : RewardBase
{
    public ItemReward()
        : base("item", RewardType.Item)
    {
    }

    public Identifier ItemId { get; set; }

    public int Count { get; set; } = 1;

    public string? Nbt { get; set; }
}

