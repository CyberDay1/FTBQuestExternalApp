namespace FTBQuestExternalApp.Codecs.Model;

public sealed class UnknownTask : TaskBase
{
    public UnknownTask(string typeId)
        : base(string.IsNullOrWhiteSpace(typeId) ? "custom" : typeId)
    {
    }
}
