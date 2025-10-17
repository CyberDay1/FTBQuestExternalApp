using FTBQuests.Core.Model;
using FTBQuests.Core.Enums;

namespace FTBQuests.Codecs.Model.Rewards
{
    public class CommandReward : RewardBase
    {
        public string? Command { get; set; }

        public CommandReward(string id) : base(id, FTBQuests.Core.Enums.RewardType.Command.ToString()) { }
    }
}







