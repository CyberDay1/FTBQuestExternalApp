using FTBQuests.Validation;
using FTBQuests.Assets;
using FTBQuestExternalApp.Codecs.Enums;

namespace FTBQuestExternalApp.Codecs.Model;

public sealed class CommandReward : RewardBase
{
    public CommandReward()
        : base("command", RewardType.Command)
    {
    }

    public string Command { get; set; } = string.Empty;
}
