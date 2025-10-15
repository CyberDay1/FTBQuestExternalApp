using System.Text.Json;
using ICSharpCode.SharpZipLib.Zip;

namespace FTBQuests.Assets;

/// <summary>
/// Builds an index of texture assets contained in Minecraft mod JAR files.
/// </summary>
public sealed class JarIconIndexer
{
    private static readonly HashSet<string> SupportedTextureFolders = new(StringComparer.OrdinalIgnoreCase)
    {
        "item",
        "items",
        "block",
        "blocks",
    };

    /// <summary>
    /// Scans the provided directory for JAR files and builds an icon index at the specified destination.
    /// </summary>
    /// <param name="jarsDirectory">The directory that contains mod JAR files.</param>
    /// <param name="outputFilePath">The full path of the JSON file that will be generated.</param>
    /// <param name="cancellationToken">The cancellation token that will abort processing.</param>
    /// <exception cref="DirectoryNotFoundException">Thrown when <paramref name="jarsDirectory"/> does not exist.</exception>
    public async Task BuildIndexAsync(string jarsDirectory, string outputFilePath, CancellationToken cancellationToken = default)
    {
        ArgumentException.ThrowIfNullOrEmpty(jarsDirectory);
        ArgumentException.ThrowIfNullOrEmpty(outputFilePath);

        if (!Directory.Exists(jarsDirectory))
        {
            throw new DirectoryNotFoundException($"The directory '{jarsDirectory}' does not exist.");
        }

        var jarFiles = Directory
            .EnumerateFiles(jarsDirectory, "*.jar", SearchOption.AllDirectories)
            .OrderBy(path => path, StringComparer.OrdinalIgnoreCase)
            .ToList();

        var index = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);

        foreach (var jarFile in jarFiles)
        {
            cancellationToken.ThrowIfCancellationRequested();

            using var stream = File.OpenRead(jarFile);
            using var zipFile = new ZipFile(stream);

            foreach (ZipEntry entry in zipFile)
            {
                cancellationToken.ThrowIfCancellationRequested();

                if (!entry.IsFile)
                {
                    continue;
                }

                var entryName = entry.Name.Replace('\\', '/');
                if (!entryName.EndsWith(".png", StringComparison.OrdinalIgnoreCase))
                {
                    continue;
                }

                if (!entryName.StartsWith("assets/", StringComparison.Ordinal))
                {
                    continue;
                }

                var segments = entryName.Split('/');
                if (segments.Length < 5)
                {
                    continue;
                }

                if (!string.Equals(segments[2], "textures", StringComparison.OrdinalIgnoreCase))
                {
                    continue;
                }

                if (!SupportedTextureFolders.Contains(segments[3]))
                {
                    continue;
                }

                var namespaceId = segments[1];
                var relativePath = string.Join('/', segments.Skip(4));

                if (string.IsNullOrWhiteSpace(namespaceId) || string.IsNullOrWhiteSpace(relativePath))
                {
                    continue;
                }

                var registryPath = relativePath[..^4]; // remove .png
                var registryId = $"{namespaceId}:{registryPath}";

                if (!index.ContainsKey(registryId))
                {
                    index[registryId] = entryName;
                }
            }
        }

        var orderedEntries = index
            .OrderBy(pair => pair.Key, StringComparer.OrdinalIgnoreCase)
            .Select(pair => new IconIndexEntry(pair.Key, pair.Value))
            .ToList();

        var outputDirectory = Path.GetDirectoryName(outputFilePath);
        if (!string.IsNullOrEmpty(outputDirectory))
        {
            Directory.CreateDirectory(outputDirectory);
        }

        var json = JsonSerializer.Serialize(orderedEntries, new JsonSerializerOptions
        {
            WriteIndented = true,
        });

        await File.WriteAllTextAsync(outputFilePath, json, cancellationToken).ConfigureAwait(false);
    }

    private sealed record IconIndexEntry(string Id, string TexturePath);
}
