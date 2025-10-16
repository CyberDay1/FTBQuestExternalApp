using FTBQuests.Validation;
using FTBQuests.Assets;
using FTBQuestExternalApp.Codecs.Enums;

namespace FTBQuestExternalApp.Codecs.Model;

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
