using FTBQuests.Core.Model;
namespace FTBQuests.Codecs.Model.Tasks;

public class CommandTask : TaskBase
{
    public override bool CheckCompletion(object context) => false;
}

