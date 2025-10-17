using FTBQuests.Core.Model;
namespace FTBQuests.Codecs.Model.Tasks;

public class CustomTask : TaskBase
{
    public override bool CheckCompletion(object context) => false;
}

