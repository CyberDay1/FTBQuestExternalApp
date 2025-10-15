// <copyright file="KnownTasks.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

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

public sealed class AdvancementTask : TaskBase
{
    public AdvancementTask()
        : base("advancement")
    {
    }

    public Identifier AdvancementId { get; set; }
}

public sealed class KillTask : TaskBase
{
    public KillTask()
        : base("kill")
    {
    }

    public Identifier EntityId { get; set; }

    public int Amount { get; set; }
}

public sealed class LocationTask : TaskBase
{
    public LocationTask()
        : base("location")
    {
    }

    public int X { get; set; }

    public int Y { get; set; }

    public int Z { get; set; }

    public string? Dimension { get; set; }

    public int Radius { get; set; }
}

public sealed class XpTask : TaskBase
{
    public XpTask()
        : base("xp")
    {
    }

    public int Amount { get; set; }

    public bool Levels { get; set; }
}

public sealed class NbtTask : TaskBase
{
    public NbtTask()
        : base("nbt")
    {
    }

    public Identifier TargetId { get; set; }

    public string? RequiredNbt { get; set; }
}

public sealed class CommandTask : TaskBase
{
    public CommandTask()
        : base("command")
    {
    }

    public string? Command { get; set; }
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
