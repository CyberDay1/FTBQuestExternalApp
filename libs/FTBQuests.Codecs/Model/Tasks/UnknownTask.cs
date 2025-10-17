using FTBQuests.Core.Model;using FTBQuests.Assets;namespace FTBQuests.Codecs.Model;public sealed class UnknownTask : TaskBase{    public UnknownTask(string typeId)        : base(string.IsNullOrWhiteSpace(typeId) ? "custom" : typeId)    {
    public override bool CheckCompletion(object context) => false;
    }}

