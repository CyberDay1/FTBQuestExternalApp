using FTBQuests.Core.Model;using FTBQuests.Assets;namespace FTBQuests.Codecs.Model;public sealed class NbtTask : TaskBase{    public NbtTask()        : base("nbt")    {    }    public Identifier TargetId { get; set; }    public string? RequiredNbt {
    public override bool CheckCompletion(object context) => false;
 get; set; }}

