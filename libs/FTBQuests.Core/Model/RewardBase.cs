namespace FTBQuests.Core.Model;

public abstract class RewardBase
{
        public string Id { get; set; } = string.Empty;
        public string Name { get; set; } = string.Empty;

        protected RewardBase() { }

        protected RewardBase(string id, string name)
        {
            Id = id;
            Name = name;
        }
    public string Title { get; set; } = string.Empty;

    public virtual void Grant()
    {
        // Implemented by subclasses in higher layers
    }
}

