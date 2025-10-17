namespace FTBQuests.Core.Model;

public abstract class TaskBase
{
    public string Id { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public bool Completed { get; protected set; }

    public abstract bool CheckCompletion(object context);

    public virtual void Reset() => Completed = false;
}
