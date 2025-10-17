using FTBQuests.Core.Model;using FTBQuests.Assets;namespace FTBQuests.Codecs.Model;public sealed class CommandTask : TaskBase{    public CommandTask()        : base("command")    {    }    public string? Command {
    public override bool CheckCompletion(object context) => false;
 get; set; }}

