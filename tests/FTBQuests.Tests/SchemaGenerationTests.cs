// <copyright file="SchemaGenerationTests.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System.IO;
using System.Linq;
using System.Text.Json;
using FTBQuestExternalApp.Codecs.Enums;
using Xunit;

namespace FTBQuests.Tests;

public class SchemaGenerationTests
{
    [Fact]
    public void QuestSchema_ShouldRequireId()
    {
        using var document = LoadSchema("quest.schema.json");
        var root = document.RootElement;
        Assert.True(root.TryGetProperty("required", out var requiredElement), "Quest schema missing required section.");

        var requiredValues = requiredElement.EnumerateArray().Select(element => element.GetString()).ToList();
        Assert.Contains("id", requiredValues);
    }

    [Fact]
    public void TaskSchema_ShouldExposeAllKnownTypes()
    {
        using var document = LoadSchema("task.schema.json");
        var typeProperty = document.RootElement.GetProperty("properties").GetProperty("type");
        var enumValues = typeProperty.GetProperty("enum").EnumerateArray().Select(element => element.GetString()).ToList();

        var expected = Enum.GetNames(typeof(TaskType))
            .Select(name => name.ToLowerInvariant())
            .OrderBy(name => name)
            .ToList();
        Assert.Equal(expected, enumValues.OrderBy(name => name));
    }

    [Fact]
    public void RewardSchema_ShouldExposeAllKnownTypes()
    {
        using var document = LoadSchema("reward.schema.json");
        var typeProperty = document.RootElement.GetProperty("properties").GetProperty("type");
        var enumValues = typeProperty.GetProperty("enum").EnumerateArray().Select(element => element.GetString()).ToList();

        var expected = Enum.GetNames(typeof(RewardType))
            .Select(name => name.ToLowerInvariant())
            .OrderBy(name => name)
            .ToList();
        Assert.Equal(expected, enumValues.OrderBy(name => name));
    }

    private static JsonDocument LoadSchema(string fileName)
    {
        var schemaPath = Path.Combine(FindRepositoryRoot(), "tools", "Schemas", "v1_21_1", fileName);
        Assert.True(File.Exists(schemaPath), $"Schema file '{schemaPath}' was not found.");
        using var stream = File.OpenRead(schemaPath);
        return JsonDocument.Parse(stream);
    }

    private static string FindRepositoryRoot()
    {
        var directory = AppContext.BaseDirectory;

        for (var i = 0; i < 6; i++)
        {
            var segments = Enumerable.Repeat("..", i + 1).Prepend(directory).ToArray();
            var potential = Path.GetFullPath(Path.Combine(segments));
            if (File.Exists(Path.Combine(potential, "FTBQuestExternalApp.sln")))
            {
                return potential;
            }
        }

        throw new InvalidOperationException("Unable to locate repository root.");
    }
}
