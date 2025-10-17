// <copyright file="RegistryImporter.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>
using System.Text.Json;

using FTBQuests.Core.Model;

using FTBQuests.Assets;

using FTBQuests.Registry.Model;

namespace FTBQuests.Registry;

/// <summary>
/// Imports registry information from the probe JSON output into an in-memory database.
/// </summary>
public sealed class RegistryImporter
{
    /// <summary>
    /// Loads registry information from a probe output directory.
    /// </summary>
    /// <param name="folder">The directory that contains the probe JSON files.</param>
    /// <returns>A populated <see cref="RegistryDatabase"/>.</returns>
    public async Task<RegistryDatabase> LoadFromProbeAsync(string folder)
    {
        ArgumentException.ThrowIfNullOrEmpty(folder);

        string resolvedFolder = Path.GetFullPath(folder);
        string registryDumpPath = Path.Combine(resolvedFolder, "registry_dump.json");
        if (!File.Exists(registryDumpPath))
        {
            throw new FileNotFoundException("Probe output is missing registry_dump.json", registryDumpPath);
        }

        await using FileStream stream = File.OpenRead(registryDumpPath);
        using JsonDocument document = await JsonDocument.ParseAsync(stream).ConfigureAwait(false);
        JsonElement root = document.RootElement;

        List<RegistryItem> items = new List<RegistryItem>();
        if (root.TryGetProperty("items", out JsonElement itemsElement) && itemsElement.ValueKind == JsonValueKind.Array)
        {
            items.AddRange(ParseItems(itemsElement));
        }

        IReadOnlyDictionary<string, IReadOnlyCollection<string>> tagMembership = root.TryGetProperty("tags", out JsonElement tagsElement)
            ? ParseTags(tagsElement)
            : new Dictionary<string, IReadOnlyCollection<string>>(StringComparer.OrdinalIgnoreCase);

        var database = new RegistryDatabase(items, tagMembership);
        Seed.VanillaRegistrySeeder.EnsureBaseItems(database);
        return database;
    }

    private static IEnumerable<RegistryItem> ParseItems(JsonElement array)
    {
        foreach (JsonElement element in array.EnumerateArray())
        {
            if (!element.TryGetProperty("id", out JsonElement idElement) || idElement.ValueKind != JsonValueKind.String)
            {
                continue;
            }

            string? identifier = idElement.GetString();
            if (string.IsNullOrWhiteSpace(identifier))
            {
                continue;
            }

            string displayName = GetString(element, "defaultName") ?? GetString(element, "descriptionId") ?? identifier;
            string? nbt = GetString(element, "nbt");
            string sourceModId = ExtractSourceModId(identifier);

            yield return new RegistryItem(identifier, displayName, nbt, sourceModId);
        }
    }

    private static IReadOnlyDictionary<string, IReadOnlyCollection<string>> ParseTags(JsonElement tagsElement)
    {
        var map = new Dictionary<string, List<string>>(StringComparer.OrdinalIgnoreCase);

        foreach (JsonProperty category in tagsElement.EnumerateObject())
        {
            JsonElement value = category.Value;
            if (value.ValueKind == JsonValueKind.Object)
            {
                foreach (JsonProperty tag in value.EnumerateObject())
                {
                    List<string> entries = GetOrCreate(map, tag.Name);
                    if (tag.Value.ValueKind != JsonValueKind.Array)
                    {
                        continue;
                    }

                    foreach (JsonElement entry in tag.Value.EnumerateArray())
                    {
                        if (entry.ValueKind == JsonValueKind.String)
                        {
                            string? identifier = entry.GetString();
                            if (!string.IsNullOrWhiteSpace(identifier))
                            {
                                entries.Add(identifier);
                            }
                        }
                    }
                }
            }
            else if (value.ValueKind == JsonValueKind.Array)
            {
                foreach (JsonElement tagName in value.EnumerateArray())
                {
                    if (tagName.ValueKind == JsonValueKind.String)
                    {
                        string? name = tagName.GetString();
                        if (!string.IsNullOrWhiteSpace(name))
                        {
                            GetOrCreate(map, name);
                        }
                    }
                }
            }
        }

        var result = new Dictionary<string, IReadOnlyCollection<string>>(map.Count, StringComparer.OrdinalIgnoreCase);
        foreach ((string tag, List<string> identifiers) in map)
        {
            result[tag] = identifiers.Count == 0 ? Array.Empty<string>() : identifiers.ToArray();
        }

        return result;
    }

    private static List<string> GetOrCreate(Dictionary<string, List<string>> map, string name)
    {
        if (!map.TryGetValue(name, out List<string>? list))
        {
            list = new List<string>();
            map[name] = list;
        }

        return list;
    }

    private static string? GetString(JsonElement element, string propertyName)
    {
        if (element.TryGetProperty(propertyName, out JsonElement property) && property.ValueKind == JsonValueKind.String)
        {
            string? value = property.GetString();
            return string.IsNullOrWhiteSpace(value) ? null : value;
        }

        return null;
    }

    private static string ExtractSourceModId(string identifier)
    {
        int separatorIndex = identifier.IndexOf(':');
        if (separatorIndex <= 0)
        {
            return identifier;
        }

        return identifier[..separatorIndex];
    }
}
