using FTBQuests.Validation;
using FTBQuests.Assets;
namespace FTBQuestExternalApp.Codecs.Model;

public sealed class AdvancementTask : TaskBase
{
    public AdvancementTask()
        : base("advancement")
    {
    }

    public Identifier AdvancementId { get; set; }
}
