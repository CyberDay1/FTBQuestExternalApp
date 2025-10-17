using FTBQuests.Codecs.Enums;

namespace FTBQuests.Codecs.Model;

public sealed class CommandReward : RewardBase
{
    public CommandReward()
        : base("command", RewardType.Command)
    {
    }

    public string Command { get; set; } = string.Empty;
}
