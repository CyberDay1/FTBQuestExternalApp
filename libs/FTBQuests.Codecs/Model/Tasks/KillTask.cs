using FTBQuests.Core.Model;

namespace FTBQuests.Codecs.Model.Tasks;

public class KillTask : TaskBase
{
    public override bool CheckCompletion(object context) => false;
}
