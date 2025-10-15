namespace FTBQuestExternalApp.Codecs.Model;

public sealed class ItemReward : RewardBase
{
    public ItemReward()
        : base("item")
    {
    }
}

public sealed class LootReward : RewardBase
{
    public LootReward()
        : base("loot")
    {
    }
}

public sealed class XpReward : RewardBase
{
    public XpReward()
        : base("xp")
    {
    }
}

public sealed class CommandReward : RewardBase
{
    public CommandReward()
        : base("command")
    {
    }
}

public sealed class CustomReward : RewardBase
{
    public CustomReward()
        : base("custom")
    {
    }
}

public sealed class UnknownReward : RewardBase
{
    public UnknownReward(string typeId)
        : base(string.IsNullOrWhiteSpace(typeId) ? "custom" : typeId)
    {
    }
}
