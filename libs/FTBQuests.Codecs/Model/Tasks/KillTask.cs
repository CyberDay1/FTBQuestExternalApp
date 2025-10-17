using FTBQuests.Core.Model;using FTBQuests.Assets;namespace FTBQuests.Codecs.Model;public sealed class KillTask : TaskBase{    public KillTask()        : base("kill")    {    }    public Identifier EntityId { get; set; }    public int Amount {
    public override bool CheckCompletion(object context) => false;
 get; set; }}

