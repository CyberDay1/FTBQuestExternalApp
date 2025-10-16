using FTBQuests.Validation;
using FTBQuests.Assets;
namespace FTBQuests.Codecs.Model;

public sealed class UnknownTask : TaskBase
{
    public UnknownTask(string typeId)
        : base(string.IsNullOrWhiteSpace(typeId) ? "custom" : typeId)
    {
    }
}

