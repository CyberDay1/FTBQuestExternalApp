using FTBQuests.Core.Model;using FTBQuests.Assets;namespace FTBQuests.Codecs.Model;public sealed class AdvancementTask : TaskBase{    public AdvancementTask()        : base("advancement")    {    }    public Identifier AdvancementId {
    public override bool CheckCompletion(object context) => false;
 get; set; }}

