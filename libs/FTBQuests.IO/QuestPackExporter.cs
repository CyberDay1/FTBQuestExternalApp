namespace FTBQuests.IO;

using System;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

public sealed class QuestPackExporter
{
    private readonly QuestPackLoader loader = new();

    public async Task ExportAsync(QuestPack pack, string zipPath, CancellationToken ct = default)
    {
        ArgumentNullException.ThrowIfNull(pack);
        ArgumentNullException.ThrowIfNull(zipPath);
        ct.ThrowIfCancellationRequested();

        var tempRoot = Path.Combine(Path.GetTempPath(), "QuestPackExport", Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(tempRoot);

        try
        {
            var configRoot = Path.Combine(tempRoot, "config", "ftbquests");
            await loader.WriteAsync(pack, configRoot, ct).ConfigureAwait(false);

            var metadataPath = Path.Combine(tempRoot, "metadata.json");
            var metadata = CreateMetadata();
            var metadataContent = metadata.ToString(Formatting.Indented);
            await File.WriteAllTextAsync(metadataPath, metadataContent, ct).ConfigureAwait(false);

            var directory = Path.GetDirectoryName(zipPath);
            if (!string.IsNullOrEmpty(directory))
            {
                Directory.CreateDirectory(directory);
            }

            if (File.Exists(zipPath))
            {
                File.Delete(zipPath);
            }

            ZipFile.CreateFromDirectory(tempRoot, zipPath, CompressionLevel.Optimal, includeBaseDirectory: false);
        }
        finally
        {
            if (Directory.Exists(tempRoot))
            {
                Directory.Delete(tempRoot, recursive: true);
            }
        }
    }

    private static JObject CreateMetadata()
    {
        var assembly = typeof(QuestPackExporter).Assembly;
        var informationalVersion = assembly
            .GetCustomAttribute<AssemblyInformationalVersionAttribute>()?
            .InformationalVersion;

        var version = informationalVersion ?? assembly.GetName().Version?.ToString() ?? "0.0.0";

        return new JObject
        {
            ["tool"] = "FTBQuestExternalApp",
            ["toolVersion"] = version,
            ["exportedAt"] = DateTimeOffset.UtcNow.ToString("O"),
        };
    }
}
