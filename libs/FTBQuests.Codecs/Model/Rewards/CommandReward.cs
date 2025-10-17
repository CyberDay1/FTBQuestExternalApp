using FTBQuests.Core.Model;

namespace FTBQuests.Codecs.Model.Rewards
{
    public class CommandReward : RewardBase
    {
        public string? Command { get; set; }

        public CommandReward(string id) : base(id, RewardType.Command.ToString()) { }
    }
}


