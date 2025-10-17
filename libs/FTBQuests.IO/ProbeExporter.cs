// <copyright file="ProbeExporter.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>
using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading.Tasks;

using FTBQuests.Core.Model;

using FTBQuests.Codecs;
using FTBQuests.Codecs.Model;

using FTBQuests.Assets;

using FTBQuests.Registry;
using FTBQuests.Registry.Model;

using Newtonsoft.Json.Linq;

namespace FTBQuests.IO;
public sealed class ProbeExporter
{
    private static readonly JsonWriterOptions WriterOptions = new()
    {
        Indented = true,
        SkipValidation = false,
    };
    public async Task ExportProbeAsync(FTBQuests.IO.QuestPack pack, RegistryDatabase db, string outFolder)
        ArgumentNullException.ThrowIfNull(pack);
        ArgumentNullException.ThrowIfNull(db);
        ArgumentException.ThrowIfNullOrEmpty(outFolder);
        string destination = Path.GetFullPath(outFolder);
        Directory.CreateDirectory(destination);
        await WriteRegistryDumpAsync(db, Path.Combine(destination, "registry_dump.json")).ConfigureAwait(false);
        await WriteLanguageIndexAsync(pack, Path.Combine(destination, "lang_index.json")).ConfigureAwait(false);
    }
    private static async Task WriteRegistryDumpAsync(RegistryDatabase db, string filePath)
        await using FileStream stream = File.Create(filePath);
        await using var writer = new Utf8JsonWriter(stream, WriterOptions);
        writer.WriteStartObject();
        writer.WritePropertyName("registryNames");
        writer.WriteStartArray();
        writer.WriteStringValue("minecraft:block");
        writer.WriteStringValue("minecraft:fluid");
        writer.WriteStringValue("minecraft:item");
        writer.WriteEndArray();
        writer.WritePropertyName("items");
        foreach (RegistryItem item in db.Items)
        {
            writer.WriteStartObject();
            writer.WriteString("id", item.Id);
            writer.WriteString("defaultName", item.DisplayName);
            if (!string.IsNullOrWhiteSpace(item.OptionalNbtTemplate))
            {
                writer.WriteString("nbt", item.OptionalNbtTemplate);
            }
            writer.WriteEndObject();
        }
        writer.WritePropertyName("blocks");
        writer.WritePropertyName("fluids");
        writer.WritePropertyName("tags");
        writer.WritePropertyName("item");
        IReadOnlyDictionary<string, IReadOnlyCollection<string>> membership = db.TagMembership;
        foreach ((string tag, IReadOnlyCollection<string> identifiers) in membership.OrderBy(static pair => pair.Key, StringComparer.Ordinal))
            writer.WritePropertyName(tag);
            writer.WriteStartArray();
            IEnumerable<string> orderedIdentifiers = identifiers.OrderBy(static FTBQuests.Core.Model.Identifier => FTBQuests.Core.Model.Identifier, StringComparer.Ordinal);
            foreach (string identifier in orderedIdentifiers)
                writer.WriteStringValue(FTBQuests.Core.Model.Identifier);
            writer.WriteEndArray();
        writer.WriteEndObject();
        writer.WritePropertyName("block");
        writer.WritePropertyName("fluid");
        await writer.FlushAsync().ConfigureAwait(false);
    private static async Task WriteLanguageIndexAsync(FTBQuests.IO.QuestPack pack, string filePath)
        SortedDictionary<string, SortedDictionary<string, string>> languages = new(StringComparer.Ordinal);
        foreach ((string key, JToken value) in pack.Metadata.Extra)
            if (value is null)
                continue;
            string normalizedKey = key.Replace('\\', '/');
            if (!normalizedKey.StartsWith("lang/", StringComparison.OrdinalIgnoreCase) || !normalizedKey.EndsWith(".json", StringComparison.OrdinalIgnoreCase))
            string afterLang = normalizedKey["lang/".Length..];
            if (string.IsNullOrWhiteSpace(afterLang))
            string languageSegment = afterLang;
            int separatorIndex = afterLang.IndexOf('/');
            if (separatorIndex >= 0)
                languageSegment = afterLang[..separatorIndex];
            if (languageSegment.EndsWith(".json", StringComparison.OrdinalIgnoreCase))
                languageSegment = Path.GetFileNameWithoutExtension(languageSegment);
            if (string.IsNullOrWhiteSpace(languageSegment))
            string languageCode = languageSegment.ToLower(CultureInfo.InvariantCulture);
            if (value is not JObject languageObject)
            if (!languages.TryGetValue(languageCode, out SortedDictionary<string, string>? translations))
                translations = new SortedDictionary<string, string>(StringComparer.Ordinal);
                languages[languageCode] = translations;
            foreach (JProperty property in languageObject.Properties())
                if (property.Value is not JValue jValue || jValue.Type != JTokenType.String)
                {
                    continue;
                }
                string translationKey = property.Name;
                string translationValue = jValue.Value<string>() ?? string.Empty;
                translations.TryAdd(translationKey, translationValue);
        foreach ((string language, SortedDictionary<string, string> translations) in languages)
            writer.WritePropertyName(language);
            foreach ((string translationKey, string translationValue) in translations)
                writer.WriteString(translationKey, translationValue);
}

