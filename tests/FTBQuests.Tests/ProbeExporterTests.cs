using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;
using System.Threading.Tasks;
using FTBQuests.IO;
using FTBQuests.Registry;
using FTBQuests.Registry.Model;
using Newtonsoft.Json.Linq;
using Xunit;

namespace FTBQuests.Tests;

public sealed class ProbeExporterTests
{
    [Fact]
    public async Task ExportProbeAsync_WritesProbeCompatibleFiles()
    {
        var pack = new QuestPack();
        pack.Metadata.Add("lang/en_us/ftbquests/quests.json", JObject.Parse("{""ftbquests.quest.example"": ""Example Quest""}"));
        pack.Metadata.Add("lang/de_de.json", JObject.Parse("{""ftbquests.quest.example"": ""Beispielauftrag""}"));

        var items = new List<RegistryItem>
        {
            new("minecraft:apple", "Apple", "{Damage:0s}", "minecraft"),
            new("example:widget", "Widget", null, "example"),
        };

        var tagMembership = new Dictionary<string, IReadOnlyCollection<string>>(StringComparer.OrdinalIgnoreCase)
        {
            ["forge:fruits"] = new[] { "minecraft:apple" },
            ["example:widgets"] = new[] { "example:widget" },
        };

        var registry = new RegistryDatabase(items, tagMembership);

        string tempDirectory = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());
        Directory.CreateDirectory(tempDirectory);

        try
        {
            var exporter = new ProbeExporter();
            await exporter.ExportProbeAsync(pack, registry, tempDirectory);

            string registryDumpPath = Path.Combine(tempDirectory, "registry_dump.json");
            string languageIndexPath = Path.Combine(tempDirectory, "lang_index.json");

            Assert.True(File.Exists(registryDumpPath));
            Assert.True(File.Exists(languageIndexPath));

            var importer = new RegistryImporter();
            RegistryDatabase roundTripped = await importer.LoadFromProbeAsync(tempDirectory);
            Assert.True(roundTripped.TryGetByIdentifier("example:widget", out RegistryItem? widget));
            Assert.NotNull(widget);
            Assert.Equal("Widget", widget!.DisplayName);

            string langJson = await File.ReadAllTextAsync(languageIndexPath);
            using JsonDocument document = JsonDocument.Parse(langJson);
            JsonElement root = document.RootElement;
            Assert.True(root.TryGetProperty("en_us", out JsonElement enUs));
            Assert.Equal("Example Quest", enUs.GetProperty("ftbquests.quest.example").GetString());
            Assert.True(root.TryGetProperty("de_de", out JsonElement deDe));
            Assert.Equal("Beispielauftrag", deDe.GetProperty("ftbquests.quest.example").GetString());
        }
        finally
        {
            Directory.Delete(tempDirectory, true);
        }
    }
}
