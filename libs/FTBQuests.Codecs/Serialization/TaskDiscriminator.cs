// <copyright file="TaskDiscriminator.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.Generic;
using FTBQuestExternalApp.Codecs.Enums;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestExternalApp.Codecs.Serialization;

public static class TaskDiscriminator
{
    public const string FieldName = "type";

    private sealed record TaskDefinition(Type ClrType, string Discriminator, Func<TaskBase> Factory);

    private static readonly IReadOnlyDictionary<TaskType, TaskDefinition> Definitions =
        new Dictionary<TaskType, TaskDefinition>
        {
            [TaskType.Item] = new(typeof(ItemTask), "item", () => new ItemTask()),
            [TaskType.Advancement] = new(typeof(AdvancementTask), "advancement", () => new AdvancementTask()),
            [TaskType.Kill] = new(typeof(KillTask), "kill", () => new KillTask()),
            [TaskType.Location] = new(typeof(LocationTask), "location", () => new LocationTask()),
            [TaskType.Xp] = new(typeof(XpTask), "xp", () => new XpTask()),
            [TaskType.Nbt] = new(typeof(NbtTask), "nbt", () => new NbtTask()),
            [TaskType.Command] = new(typeof(CommandTask), "command", () => new CommandTask()),
            [TaskType.Custom] = new(typeof(CustomTask), "custom", () => new CustomTask()),
        };

    private static readonly IReadOnlyDictionary<string, TaskType> DiscriminatorToTaskType =
        BuildDiscriminatorLookup();

    private static readonly IReadOnlyDictionary<Type, TaskType> TypeToTaskType = BuildTypeLookup();

    public static string GetDiscriminator(TaskType taskType)
    {
        if (!Definitions.TryGetValue(taskType, out var definition))
        {
            throw new ArgumentOutOfRangeException(nameof(taskType), taskType, "Unknown task type.");
        }

        return definition.Discriminator;
    }

    public static Type GetClrType(TaskType taskType)
    {
        if (!Definitions.TryGetValue(taskType, out var definition))
        {
            throw new ArgumentOutOfRangeException(nameof(taskType), taskType, "Unknown task type.");
        }

        return definition.ClrType;
    }

    public static bool TryGetTaskType(string? discriminator, out TaskType taskType)
    {
        if (string.IsNullOrWhiteSpace(discriminator))
        {
            taskType = TaskType.Custom;
            return false;
        }

        return DiscriminatorToTaskType.TryGetValue(discriminator, out taskType);
    }

    public static bool TryGetTaskType(Type clrType, out TaskType taskType)
    {
        return TypeToTaskType.TryGetValue(clrType, out taskType);
    }

    public static TaskBase Create(TaskType taskType)
    {
        if (!Definitions.TryGetValue(taskType, out var definition))
        {
            throw new ArgumentOutOfRangeException(nameof(taskType), taskType, "Unknown task type.");
        }

        return definition.Factory();
    }

    public static TaskBase Create(string? discriminator)
    {
        if (TryGetTaskType(discriminator, out var taskType))
        {
            var task = Create(taskType);
            task.SetTypeId(discriminator);
            return task;
        }

        var fallback = string.IsNullOrEmpty(discriminator) ? "custom" : discriminator!;
        var unknownTask = new UnknownTask(fallback);
        unknownTask.SetTypeId(discriminator);
        return unknownTask;
    }

    private static IReadOnlyDictionary<string, TaskType> BuildDiscriminatorLookup()
    {
        var dictionary = new Dictionary<string, TaskType>(StringComparer.OrdinalIgnoreCase);

        foreach (var kvp in Definitions)
        {
            dictionary[kvp.Value.Discriminator] = kvp.Key;
        }

        return dictionary;
    }

    private static IReadOnlyDictionary<Type, TaskType> BuildTypeLookup()
    {
        var dictionary = new Dictionary<Type, TaskType>();

        foreach (var kvp in Definitions)
        {
            dictionary[kvp.Value.ClrType] = kvp.Key;
        }

        return dictionary;
    }
}
