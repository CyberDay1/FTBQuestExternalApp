using FTBQuests.Validation;
using FTBQuests.Assets;
namespace FTBQuestExternalApp.Codecs.Model;

public sealed class NbtTask : TaskBase
{
    public NbtTask()
        : base("nbt")
    {
    }

    public Identifier TargetId { get; set; }

    public string? RequiredNbt { get; set; }
}
