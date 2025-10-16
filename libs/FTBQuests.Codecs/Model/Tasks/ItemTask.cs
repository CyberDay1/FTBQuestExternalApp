using FTBQuests.Validation;
using FTBQuests.Assets;
namespace FTBQuestExternalApp.Codecs.Model;

public sealed class ItemTask : TaskBase
{
    public ItemTask()
        : base("item")
    {
    }

    public Identifier ItemId { get; set; }

    public int Count { get; set; }

    public string? Nbt { get; set; }
}
