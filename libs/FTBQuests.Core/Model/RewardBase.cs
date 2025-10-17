namespace FTBQuests.Core.Model;

public abstract class RewardBase
{
    public string Id { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;

    public virtual void Grant()
    {
        // Implemented by subclasses in higher layers
    }
}
