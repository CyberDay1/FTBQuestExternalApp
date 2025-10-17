using FTBQuests.Core.Model;

namespace FTBQuests.Codecs.Model.Tasks;

public class AdvancementTask : TaskBase
{
    public override bool CheckCompletion(object context) => false;
}
