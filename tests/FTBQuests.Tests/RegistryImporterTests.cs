using FTBQuests.Registry;
using FTBQuests.Registry.Model;
using Xunit;

namespace FTBQuests.Tests;

public sealed class RegistryImporterTests
{
    [Fact]
    public async Task LoadFromProbeAsync_ImportsItemsAndTags()
    {
        string tempDirectory = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());
        Directory.CreateDirectory(tempDirectory);

        try
        {
            string registryDump = """
            {
              "items": [
                {"id": "minecraft:apple", "defaultName": "Apple", "nbt": "{Damage:0s}"},
                {"id": "example:widget", "defaultName": "Widget"}
              ],
              "tags": {
                "item": {
                  "forge:fruits": ["minecraft:apple"],
                  "example:widgets": ["example:widget"]
                }
              }
            }
            """;

            await File.WriteAllTextAsync(Path.Combine(tempDirectory, "registry_dump.json"), registryDump);

            var importer = new RegistryImporter();
            RegistryDatabase database = await importer.LoadFromProbeAsync(tempDirectory);

            Assert.True(database.TryGetByIdentifier("minecraft:apple", out RegistryItem? apple));
            Assert.NotNull(apple);
            Assert.Equal("Apple", apple!.DisplayName);
            Assert.Equal("{Damage:0s}", apple.OptionalNbtTemplate);
            Assert.Equal("minecraft", apple.SourceModId);

            IReadOnlyList<RegistryItem> fruits = database.GetByTag("forge:fruits");
            Assert.Collection(fruits, item => Assert.Equal("minecraft:apple", item.Id));

            IReadOnlyList<RegistryItem> widgets = database.GetBySourceModId("example");
            Assert.Collection(widgets, item => Assert.Equal("example:widget", item.Id));

            Assert.Empty(database.GetByTag("unknown:tag"));
        }
        finally
        {
            Directory.Delete(tempDirectory, true);
        }
    }

    [Fact]
    public async Task LoadFromProbeAsync_AllowsTagNameArrays()
    {
        string tempDirectory = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString());
        Directory.CreateDirectory(tempDirectory);

        try
        {
            string registryDump = """
            {
              "items": [
                {"id": "minecraft:stick", "defaultName": "Stick"}
              ],
              "tags": {
                "item": [
                  "minecraft:sticks"
                ]
              }
            }
            """;

            await File.WriteAllTextAsync(Path.Combine(tempDirectory, "registry_dump.json"), registryDump);

            var importer = new RegistryImporter();
            RegistryDatabase database = await importer.LoadFromProbeAsync(tempDirectory);

            Assert.True(database.TryGetByIdentifier("minecraft:stick", out RegistryItem? stick));
            Assert.NotNull(stick);
            Assert.Empty(database.GetByTag("minecraft:sticks"));
        }
        finally
        {
            Directory.Delete(tempDirectory, true);
        }
    }
}
