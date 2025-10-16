using FTBQuests.Validation;
using FTBQuests.Assets;
namespace FTBQuestExternalApp.Codecs.Model;

public sealed class KillTask : TaskBase
{
    public KillTask()
        : base("kill")
    {
    }

    public Identifier EntityId { get; set; }

    public int Amount { get; set; }
}
