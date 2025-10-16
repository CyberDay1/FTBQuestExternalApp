using FTBQuests.Validation;
using FTBQuests.Assets;
namespace FTBQuestExternalApp.Codecs.Model;

public sealed class CommandTask : TaskBase
{
    public CommandTask()
        : base("command")
    {
    }

    public string? Command { get; set; }
}
