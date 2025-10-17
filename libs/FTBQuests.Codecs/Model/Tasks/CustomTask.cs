using FTBQuests.Core.Model;using FTBQuests.Assets;namespace FTBQuests.Codecs.Model;public sealed class CustomTask : TaskBase{    public CustomTask()        : base("custom")    {
    public override bool CheckCompletion(object context) => false;
    }}

