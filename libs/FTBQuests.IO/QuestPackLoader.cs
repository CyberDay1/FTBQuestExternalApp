using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using FTBQuestExternalApp.Codecs.Model;
using FTBQuests.Codecs;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace FTBQuests.IO;

public class QuestPackLoader
{
    public async Task<QuestPack> LoadAsync(string rootPath, CancellationToken ct = default)
    {
        ArgumentNullException.ThrowIfNull(rootPath);
        ct.ThrowIfCancellationRequested();

        var pack = new QuestPack();
        var ftbRoot = Path.Combine(rootPath, "data", "ftbquests");
        if (!Directory.Exists(ftbRoot))
        {
            return pack;
        }

        var serializer = JsonSerializer.Create(JsonSettings.Create());
        var metadataOrder = new List<string>();

        foreach (var metadataFile in EnumerateMetadataFiles(ftbRoot))
        {
            ct.ThrowIfCancellationRequested();

            var relativeKey = NormalizeRelativePath(ftbRoot, metadataFile);
            metadataOrder.Add(relativeKey);

            var json = await File.ReadAllTextAsync(metadataFile, ct).ConfigureAwait(false);
            var token = string.IsNullOrWhiteSpace(json) ? JValue.CreateNull() : JToken.Parse(json);
            pack.SetMetadata(relativeKey, token);
        }

        pack.SetMetadataOrder(metadataOrder);

        var chaptersRoot = Path.Combine(ftbRoot, "chapters");
        if (!Directory.Exists(chaptersRoot))
        {
            return pack;
        }

        foreach (var chapterFile in EnumerateChapterFiles(chaptersRoot))
        {
            ct.ThrowIfCancellationRequested();

            await using var stream = File.OpenRead(chapterFile);
            using var reader = new StreamReader(stream);
            using var jsonReader = new JsonTextReader(reader);
            var chapter = serializer.Deserialize<Chapter>(jsonReader);
            if (chapter is null)
            {
                continue;
            }

            var relativePath = NormalizeRelativePath(ftbRoot, chapterFile);
            pack.AddChapter(chapter, relativePath);
        }

        return pack;
    }

    public async Task SaveAsync(QuestPack pack, string rootPath, CancellationToken ct = default)
    {
        ArgumentNullException.ThrowIfNull(pack);
        ArgumentNullException.ThrowIfNull(rootPath);
        ct.ThrowIfCancellationRequested();

        var ftbRoot = Path.Combine(rootPath, "data", "ftbquests");
        Directory.CreateDirectory(ftbRoot);

        var metadataTokens = pack.Metadata.Extra;
        var metadataWritten = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

        foreach (var key in pack.EnumerateMetadataKeysInOrder())
        {
            if (!metadataTokens.TryGetValue(key, out var token))
            {
                continue;
            }

            ct.ThrowIfCancellationRequested();

            var destination = Path.Combine(ftbRoot, key.Replace('/', Path.DirectorySeparatorChar));
            var directory = Path.GetDirectoryName(destination);
            if (!string.IsNullOrEmpty(directory))
            {
                Directory.CreateDirectory(directory);
            }

            var content = token.ToString(Formatting.Indented);
            await File.WriteAllTextAsync(destination, content, ct).ConfigureAwait(false);
            metadataWritten.Add(NormalizeRelativePath(ftbRoot, destination));
        }

        foreach (var metadataFile in EnumerateMetadataFiles(ftbRoot).ToList())
        {
            var relativePath = NormalizeRelativePath(ftbRoot, metadataFile);
            if (!metadataWritten.Contains(relativePath))
            {
                File.Delete(metadataFile);
            }
        }

        var chaptersRoot = Path.Combine(ftbRoot, "chapters");
        Directory.CreateDirectory(chaptersRoot);

        var serializer = JsonSerializer.Create(JsonSettings.Create());
        var writtenChapterFiles = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

        for (var index = 0; index < pack.Chapters.Count; index++)
        {
            ct.ThrowIfCancellationRequested();

            var chapter = pack.Chapters[index];
            var relativePath = pack.GetChapterPath(chapter);
            if (string.IsNullOrEmpty(relativePath))
            {
                relativePath = GenerateChapterPath(index, writtenChapterFiles);
            }

            var normalizedRelativePath = NormalizeRelativePath(ftbRoot, Path.Combine(ftbRoot, relativePath));
            var destination = Path.Combine(ftbRoot, normalizedRelativePath.Replace('/', Path.DirectorySeparatorChar));
            var directory = Path.GetDirectoryName(destination);
            if (!string.IsNullOrEmpty(directory))
            {
                Directory.CreateDirectory(directory);
            }

            await using var stream = File.Create(destination);
            await using var writer = new StreamWriter(stream);
            using var jsonWriter = new JsonTextWriter(writer)
            {
                Formatting = Formatting.Indented,
            };

            serializer.Serialize(jsonWriter, chapter);
            await jsonWriter.FlushAsync().ConfigureAwait(false);
            await writer.FlushAsync().ConfigureAwait(false);

            var savedRelativePath = NormalizeRelativePath(ftbRoot, destination);
            writtenChapterFiles.Add(savedRelativePath);
            pack.SetChapterPath(chapter, savedRelativePath);
        }

        foreach (var existingChapterFile in EnumerateChapterFiles(chaptersRoot))
        {
            var relativePath = NormalizeRelativePath(ftbRoot, existingChapterFile);
            if (!writtenChapterFiles.Contains(relativePath))
            {
                File.Delete(existingChapterFile);
            }
        }
    }

    private static IEnumerable<string> EnumerateMetadataFiles(string rootPath)
    {
        if (!Directory.Exists(rootPath))
        {
            yield break;
        }

        var chaptersPath = Path.Combine(rootPath, "chapters");

        foreach (var file in Directory.EnumerateFiles(rootPath, "*.json", SearchOption.AllDirectories))
        {
            if (chaptersPath is not null && IsSubPath(file, chaptersPath))
            {
                continue;
            }

            yield return file;
        }
    }

    private static IEnumerable<string> EnumerateChapterFiles(string chaptersRoot)
    {
        if (!Directory.Exists(chaptersRoot))
        {
            yield break;
        }

        foreach (var file in Directory.EnumerateFiles(chaptersRoot, "*.json", SearchOption.AllDirectories))
        {
            yield return file;
        }
    }

    private static bool IsSubPath(string candidate, string possibleParent)
    {
        var relative = Path.GetRelativePath(possibleParent, candidate);
        return !relative.StartsWith("..", StringComparison.Ordinal)
               && !Path.IsPathRooted(relative);
    }

    private static string NormalizeRelativePath(string rootPath, string fullPath)
    {
        var relative = Path.GetRelativePath(rootPath, fullPath);
        var normalized = relative.Replace(Path.DirectorySeparatorChar, '/');
        return normalized.Replace(Path.AltDirectorySeparatorChar, '/');
    }

    private static string GenerateChapterPath(int index, ISet<string> usedPaths)
    {
        var fileName = $"chapters/chapter_{index + 1:D2}.json";
        var candidate = fileName;
        var counter = index + 1;

        while (usedPaths.Contains(candidate))
        {
            counter++;
            candidate = $"chapters/chapter_{counter:D2}.json";
        }

        return candidate;
    }
}
