using FTBQuests.Core.Model;using FTBQuests.Assets;namespace FTBQuests.Codecs.Model;public sealed class XpTask : TaskBase{    public XpTask()        : base("xp")    {    }    public int Amount { get; set; }    public bool Levels {
    public override bool CheckCompletion(object context) => false;
 get; set; }}

