using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="QuestPackLoader.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

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
    public Task<FTBQuests.IO.QuestPack> LoadAsync(string rootPath, CancellationToken ct = default)
    {
        return LoadAsync(rootPath, options: null, ct);
    }

    public async Task<FTBQuests.IO.QuestPack> LoadAsync(string rootPath, ImportOptions? options, CancellationToken ct = default)
    {
        ArgumentNullException.ThrowIfNull(rootPath);
        ct.ThrowIfCancellationRequested();

        options ??= new ImportOptions();

        var pack = new FTBQuests.IO.QuestPack();
        var ftbRoot = ResolveQuestRoot(rootPath, options);

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

    public async Task SaveAsync(FTBQuests.IO.QuestPack pack, string rootPath, CancellationToken ct = default)
    {
        ArgumentNullException.ThrowIfNull(pack);
        ArgumentNullException.ThrowIfNull(rootPath);
        ct.ThrowIfCancellationRequested();

        var ftbRoot = Path.Combine(rootPath, "data", "ftbquests");
        await WriteAsync(pack, ftbRoot, ct).ConfigureAwait(false);
    }

    internal async Task WriteAsync(FTBQuests.IO.QuestPack pack, string ftbRoot, CancellationToken ct)
    {
        ArgumentNullException.ThrowIfNull(pack);
        ArgumentNullException.ThrowIfNull(ftbRoot);
        ct.ThrowIfCancellationRequested();

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

    private static string ResolveQuestRoot(string rootPath, ImportOptions options)
    {
        var basePath = ResolveBasePath(rootPath, options.RootPath);
        var candidates = BuildCandidateRoots(basePath, options.PreferConfigPath).ToList();

        foreach (var candidate in candidates)
        {
            if (Directory.Exists(candidate))
            {
                return candidate;
            }
        }

        var searched = candidates.Count == 0
            ? basePath
            : string.Join(", ", candidates.Select(path => $"'{path}'"));

        throw new DirectoryNotFoundException(
            $"Could not find an FTB Quests directory under '{basePath}'. Checked: {searched}.");
    }

    private static string ResolveBasePath(string rootPath, string additional)
    {
        var resolvedRoot = Path.GetFullPath(rootPath);

        if (string.IsNullOrWhiteSpace(additional))
        {
            return resolvedRoot;
        }

        if (Path.IsPathRooted(additional))
        {
            return Path.GetFullPath(additional);
        }

        return Path.GetFullPath(Path.Combine(resolvedRoot, additional));
    }

    private static IEnumerable<string> BuildCandidateRoots(string basePath, bool preferConfig)
    {
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

        foreach (var candidate in EnumerateRootCandidates(basePath, preferConfig))
        {
            var normalized = Path.GetFullPath(candidate);
            if (seen.Add(normalized))
            {
                yield return normalized;
            }
        }
    }

    private static IEnumerable<string> EnumerateRootCandidates(string basePath, bool preferConfig)
    {
        var trimmed = Path.TrimEndingDirectorySeparator(basePath);
        if (string.Equals(Path.GetFileName(trimmed), "ftbquests", StringComparison.OrdinalIgnoreCase))
        {
            yield return basePath;
        }

        var configPath = Path.Combine(basePath, "config", "ftbquests");
        var dataPath = Path.Combine(basePath, "data", "ftbquests");

        if (preferConfig)
        {
            yield return configPath;
            yield return dataPath;
        }
        else
        {
            yield return dataPath;
            yield return configPath;
        }
    }
}
