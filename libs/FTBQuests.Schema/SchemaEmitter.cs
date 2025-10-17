using FTBQuests.Core.Model;
using FTBQuests.Assets;
// <copyright file="SchemaEmitter.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System.IO;
using System.Linq;
using System.Reflection;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;
using FTBQuests.Codecs.Enums;
using FTBQuests.Codecs.Model;

namespace FTBQuests.Schema;

/// <summary>
/// Emits JSON Schema documents that describe the serialized quest data model.
/// </summary>
public sealed class SchemaEmitter
{
    private const string SchemaDraft = "https://json-schema.org/draft/2020-12/schema";
    private const string SchemaBaseUrl = "https://ftbquests.app/schemas/v1_21_1/";

    private readonly JsonSerializerOptions serializerOptions = new(JsonSerializerDefaults.General)
    {
        WriteIndented = true,
    };

    /// <summary>
    /// Emits all quest related schemas to the provided output directory.
    /// </summary>
    /// <param name="outputDirectory">The directory that will contain the generated schema files.</param>
    public void Emit(string outputDirectory)
    {
        if (string.IsNullOrWhiteSpace(outputDirectory))
        {
            throw new ArgumentException("Output directory must be provided.", nameof(outputDirectory));
        }

        Directory.CreateDirectory(outputDirectory);

        WriteSchema(Path.Combine(outputDirectory, "task.schema.json"), BuildTaskSchema());
        WriteSchema(Path.Combine(outputDirectory, "reward.schema.json"), BuildRewardSchema());
        WriteSchema(Path.Combine(outputDirectory, "quest.schema.json"), BuildQuestSchema());
        WriteSchema(Path.Combine(outputDirectory, "chapter.schema.json"), BuildChapterSchema());
    }

    private void WriteSchema(string path, JsonObject schema)
    {
        using var stream = File.Open(path, FileMode.Create, FileAccess.Write, FileShare.None);
        using var writer = new Utf8JsonWriter(stream, new JsonWriterOptions { Indented = serializerOptions.WriteIndented });
        schema.WriteTo(writer, serializerOptions);
    }

    private static JsonObject CreateRoot(string name)
    {
        return new JsonObject
        {
            ["$schema"] = SchemaDraft,
            ["$id"] = SchemaBaseUrl + name + ".schema.json",
            ["title"] = name[0].ToString().ToUpperInvariant() + name[1..],
            ["type"] = "object",
            ["additionalProperties"] = true,
        };
    }

    private static JsonArray CreateTypeArray(params string[] values)
    {
        var array = new JsonArray();
        foreach (var value in values)
        {
            array.Add(value);
        }

        return array;
    }

    private static JsonObject CreateStringSchema(bool allowNull, string? format = null, string? pattern = null)
    {
        var schema = new JsonObject
        {
            ["type"] = allowNull ? CreateTypeArray("string", "null") : JsonValue.Create("string"),
        };

        if (!string.IsNullOrWhiteSpace(format))
        {
            schema["format"] = format;
        }

        if (!string.IsNullOrWhiteSpace(pattern))
        {
            schema["pattern"] = pattern;
        }

        return schema;
    }

    private static JsonObject CreateIntegerSchema(bool allowNull)
    {
        return new JsonObject
        {
            ["type"] = allowNull ? CreateTypeArray("integer", "null") : JsonValue.Create("integer"),
        };
    }

    private JsonObject BuildTaskSchema()
    {
        var schema = CreateRoot("task");
        schema["required"] = new JsonArray("type");

        var properties = new JsonObject();
        properties["type"] = new JsonObject
        {
            ["type"] = "string",
            ["enum"] = BuildEnumArray<TaskType>(),
            ["description"] = "Discriminator for the task subtype.",
        };

        schema["properties"] = properties;
        return schema;
    }

    private JsonObject BuildRewardSchema()
    {
        var schema = CreateRoot("reward");
        schema["required"] = new JsonArray("type");

        var properties = new JsonObject();
        properties["type"] = new JsonObject
        {
            ["type"] = "string",
            ["enum"] = BuildEnumArray<RewardType>(),
            ["description"] = "Discriminator for the reward subtype.",
        };

        schema["properties"] = properties;
        return schema;
    }

    private JsonObject BuildQuestSchema()
    {
        var schema = CreateRoot("quest");
        schema["required"] = new JsonArray("id");

        var properties = new JsonObject();
        var identifierPattern = GetIdentifierPattern();

        properties["id"] = CreateStringSchema(allowNull: false, format: "uuid");
        properties["title"] = CreateStringSchema(allowNull: true);
        properties["subtitle"] = CreateStringSchema(allowNull: true);
        properties["icon"] = CreateStringSchema(allowNull: true, pattern: identifierPattern);
        properties["iconId"] = CreateStringSchema(allowNull: true, pattern: identifierPattern);
        properties["icon_id"] = CreateStringSchema(allowNull: true, pattern: identifierPattern);
        properties["x"] = CreateIntegerSchema(allowNull: true);
        properties["y"] = CreateIntegerSchema(allowNull: true);
        properties["page"] = CreateIntegerSchema(allowNull: true);
        properties["dependencies"] = new JsonObject
        {
            ["type"] = CreateTypeArray("array", "null"),
            ["items"] = CreateStringSchema(allowNull: false, format: "uuid"),
        };
        properties["tasks"] = new JsonObject
        {
            ["type"] = CreateTypeArray("array", "null"),
            ["items"] = new JsonObject { ["$ref"] = SchemaBaseUrl + "task.schema.json" },
        };
        properties["rewards"] = new JsonObject
        {
            ["type"] = CreateTypeArray("array", "null"),
            ["items"] = new JsonObject { ["$ref"] = SchemaBaseUrl + "reward.schema.json" },
        };

        schema["properties"] = properties;
        return schema;
    }

    private JsonObject BuildChapterSchema()
    {
        var schema = CreateRoot("chapter");
        schema["required"] = new JsonArray("id", "quests");

        var properties = new JsonObject();
        var identifierPattern = GetIdentifierPattern();

        properties["id"] = CreateStringSchema(allowNull: false, format: "uuid");
        properties["title"] = CreateStringSchema(allowNull: true);
        properties["description"] = CreateStringSchema(allowNull: true);
        properties["icon"] = CreateStringSchema(allowNull: true, pattern: identifierPattern);
        properties["iconId"] = CreateStringSchema(allowNull: true, pattern: identifierPattern);
        properties["icon_id"] = CreateStringSchema(allowNull: true, pattern: identifierPattern);
        properties["quests"] = new JsonObject
        {
            ["type"] = "array",
            ["items"] = new JsonObject { ["$ref"] = SchemaBaseUrl + "quest.schema.json" },
        };

        schema["properties"] = properties;
        return schema;
    }

    private static JsonArray BuildEnumArray<TEnum>()
        where TEnum : struct, Enum
    {
        var values = Enum.GetNames(typeof(TEnum))
            .Select(name => name.ToLowerInvariant())
            .Distinct(StringComparer.Ordinal)
            .OrderBy(name => name, StringComparer.Ordinal);

        var array = new JsonArray();
        foreach (var value in values)
        {
            array.Add(value);
        }

        return array;
    }

    private static string GetIdentifierPattern()
    {
        var patternField = typeof(Identifier).GetField("Pattern", BindingFlags.Static | BindingFlags.NonPublic);
        if (patternField?.GetValue(null) is Regex regex)
        {
            return regex.ToString();
        }

        return "^[a-z0-9_.-]+:[a-z0-9_./-]+$";
    }
}


