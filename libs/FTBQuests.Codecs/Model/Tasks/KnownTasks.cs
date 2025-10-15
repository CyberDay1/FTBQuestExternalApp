namespace FTBQuestExternalApp.Codecs.Model;

public sealed class ItemTask : TaskBase
{
    public ItemTask()
        : base("item")
    {
    }
}

public sealed class AdvancementTask : TaskBase
{
    public AdvancementTask()
        : base("advancement")
    {
    }
}

public sealed class KillTask : TaskBase
{
    public KillTask()
        : base("kill")
    {
    }
}

public sealed class LocationTask : TaskBase
{
    public LocationTask()
        : base("location")
    {
    }
}

public sealed class XpTask : TaskBase
{
    public XpTask()
        : base("xp")
    {
    }
}

public sealed class NbtTask : TaskBase
{
    public NbtTask()
        : base("nbt")
    {
    }
}

public sealed class CommandTask : TaskBase
{
    public CommandTask()
        : base("command")
    {
    }
}

public sealed class CustomTask : TaskBase
{
    public CustomTask()
        : base("custom")
    {
    }
}

public sealed class UnknownTask : TaskBase
{
    public UnknownTask(string typeId)
        : base(string.IsNullOrWhiteSpace(typeId) ? "custom" : typeId)
    {
    }
}
