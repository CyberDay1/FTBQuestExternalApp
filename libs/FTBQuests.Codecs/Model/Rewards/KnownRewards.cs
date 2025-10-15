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

public sealed class LootReward : RewardBase
{
    public LootReward()
        : base("loot", RewardType.Loot)
    {
    }

    public Identifier LootTable { get; set; }
}

public sealed class LootTableReward : RewardBase
{
    public LootTableReward()
        : base("loot_table", RewardType.LootTable)
    {
    }

    public string TableName { get; set; } = string.Empty;
}

public sealed class XpReward : RewardBase
{
    public XpReward()
        : base("xp", RewardType.Xp)
    {
    }

    public int Amount { get; set; }

    public bool Levels { get; set; }
}

public sealed class CommandReward : RewardBase
{
    public CommandReward()
        : base("command", RewardType.Command)
    {
    }

    public string Command { get; set; } = string.Empty;
}

public sealed class CustomReward : RewardBase
{
    public CustomReward()
        : base("custom", RewardType.Custom)
    {
    }
}

public sealed class UnknownReward : RewardBase
{
    public UnknownReward(string typeId)
        : base(string.IsNullOrWhiteSpace(typeId) ? "custom" : typeId, RewardType.Custom)
    {
    }
}
