// <copyright file="Program.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using FTBQuestExternalApp.Codecs.Model;
using FTBQuests.IO;
using FTBQuests.Registry;
using FTBQuests.Schema;
using FTBQuests.Validation;
using FTBQuests.Validation.Validators;

// Explicit alias to avoid ambiguity between model and IO QuestPack definitions
using QuestPackModel = FTBQuestExternalApp.Codecs.Model.QuestPack;

return await DevCliApp.RunAsync(args);

internal static class DevCliApp
{
    public static async Task<int> RunAsync(string[] args)
    {
        if (args.Length == 0)
        {
            PrintUsage();
            return 1;
        }

        var command = args[0];
        var commandArgs = args.Skip(1).ToArray();

        try
        {
            return command.ToLowerInvariant() switch
            {
                "schema" => await RunSchemaAsync(commandArgs),
                "export-probe" => await RunExportProbeAsync(commandArgs),
                "validate" => await RunValidateAsync(commandArgs),
                _ => UnknownCommand(command),
            };
        }
        catch (CliException ex)
        {
            Console.Error.WriteLine(ex.Message);
            return 1;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"Unhandled error: {ex.Message}");
            return 1;
        }
    }

    private static int UnknownCommand(string command)
    {
        Console.Error.WriteLine($"Unknown command '{command}'.");
        PrintUsage();
        return 1;
    }

    private static Task<int> RunSchemaAsync(string[] args)
    {
        if (args.Length == 0)
        {
            throw new CliException("Missing schema subcommand. Expected 'emit'.");
        }

        var subCommand = args[0];
        var optionArgs = args.Skip(1).ToArray();

        return subCommand.ToLowerInvariant() switch
        {
            "emit" => Task.FromResult(RunSchemaEmit(optionArgs)),
            _ => throw new CliException($"Unknown schema subcommand '{subCommand}'."),
        };
    }

    private static int RunSchemaEmit(string[] args)
    {
        var options = OptionParser.Parse(args);
        var output = options.TryGetValue("out", out var value) && !string.IsNullOrWhiteSpace(value)
            ? value
            : Path.Combine("tools", "Schemas", "v1_21_1");

        var fullOutput = Path.GetFullPath(output, Directory.GetCurrentDirectory());
        Directory.CreateDirectory(fullOutput);

        var emitter = new SchemaEmitter();
        emitter.Emit(fullOutput);

        Console.WriteLine($"Schemas written to {fullOutput}.");
        return 0;
    }

    private static async Task<int> RunExportProbeAsync(string[] args)
    {
        var options = OptionParser.Parse(args);
        if (!options.TryGetValue("out", out var outOption) || string.IsNullOrWhiteSpace(outOption))
        {
            throw new CliException("The --out option is required.");
        }

        string outputPath = Path.GetFullPath(outOption!, Directory.GetCurrentDirectory());
        Directory.CreateDirectory(outputPath);

        string packPath = options.TryGetValue("pack", out var packOption) && !string.IsNullOrWhiteSpace(packOption)
            ? packOption!
            : Directory.GetCurrentDirectory();
        string fullPackPath = Path.GetFullPath(packPath, Directory.GetCurrentDirectory());
        if (!Directory.Exists(fullPackPath))
        {
            throw new CliException($"Pack directory '{fullPackPath}' does not exist.");
        }

        string registryPath = options.TryGetValue("registry", out var registryOption) && !string.IsNullOrWhiteSpace(registryOption)
            ? registryOption!
            : fullPackPath;
        string resolvedRegistryPath = Path.GetFullPath(registryPath, Directory.GetCurrentDirectory());
        if (File.Exists(resolvedRegistryPath))
        {
            resolvedRegistryPath = Path.GetDirectoryName(resolvedRegistryPath) ?? resolvedRegistryPath;
        }

        if (!Directory.Exists(resolvedRegistryPath))
        {
            throw new CliException($"Registry directory '{resolvedRegistryPath}' does not exist.");
        }

        var loader = new QuestPackLoader();
        FTBQuests.IO.QuestPack pack = await loader.LoadAsync(fullPackPath).ConfigureAwait(false);

        var importer = new RegistryImporter();
        RegistryDatabase registry = await importer.LoadFromProbeAsync(resolvedRegistryPath).ConfigureAwait(false);

        var exporter = new ProbeExporter();
        var ioPack = await loader.LoadAsync(fullPackPath).ConfigureAwait(false);
        var modelPack = ConvertToModelPack(ioPack);
        await exporter.ExportProbeAsync(modelPack, registry, outputPath).ConfigureAwait(false);

        Console.WriteLine($"Probe export written to {outputPath}.");
        return 0;
    }

    private static async Task<int> RunValidateAsync(string[] args)
    {
        var options = OptionParser.Parse(args);
        if (!options.TryGetValue("pack", out var packPath) || string.IsNullOrWhiteSpace(packPath))
        {
            throw new CliException("The --pack option is required.");
        }

        var fullPackPath = Path.GetFullPath(packPath, Directory.GetCurrentDirectory());
        if (!Directory.Exists(fullPackPath))
        {
            throw new CliException($"Pack directory '{fullPackPath}' does not exist.");
        }

        var loader = new QuestPackLoader();
        var ioPack = await loader.LoadAsync(fullPackPath).ConfigureAwait(false);
        var modelPack = ConvertToModelPack(ioPack);

        var validators = GetValidators();
        var issues = new List<ValidationIssue>();
        foreach (var validator in validators)
        {
            issues.AddRange(validator.Validate(modelPack));
        }

        if (issues.Count == 0)
        {
            Console.WriteLine("No validation issues detected.");
            return 0;
        }

        var ordered = issues
            .OrderByDescending(issue => issue.Severity)
            .ThenBy(issue => issue.Path, StringComparer.Ordinal)
            .ThenBy(issue => issue.Code, StringComparer.Ordinal)
            .ToList();

        Console.Error.WriteLine($"Validation issues ({ordered.Count}):");
        foreach (var issue in ordered)
        {
            var severity = issue.Severity.ToString().ToUpperInvariant();
            Console.Error.WriteLine($" - [{severity}] {issue.Path}: {issue.Message} ({issue.Code})");
        }

        return 1;
    }

    private static QuestPackModel ConvertToModelPack(FTBQuests.IO.QuestPack ioPack)
    {
        ArgumentNullException.ThrowIfNull(ioPack);

        var result = new QuestPackModel();
        foreach (var chapter in ioPack.Chapters)
        {
            result.AddChapter(chapter);
        }

        return result;
    }

    private static IReadOnlyList<IValidator> GetValidators()
    {
        return new List<IValidator>
        {
            new RequiredFieldsValidator(),
            new BrokenReferenceValidator(),
        };
    }

    private static void PrintUsage()
    {
        Console.WriteLine("FTB Quests Dev CLI");
        Console.WriteLine();
        Console.WriteLine("Usage:");
        Console.WriteLine("  dotnet run --project tools/DevCli -- schema emit [--out <directory>]");
        Console.WriteLine("  dotnet run --project tools/DevCli -- export-probe --out <directory> [--pack <directory>] [--registry <directory>]");
        Console.WriteLine("  dotnet run --project tools/DevCli -- validate --pack <directory>");
        Console.WriteLine();
        Console.WriteLine("Commands:");
        Console.WriteLine("  schema emit   Emit JSON schemas for quest data.");
        Console.WriteLine("  export-probe  Export quest content to probe-compatible JSON.");
        Console.WriteLine("  validate      Validate a quest pack directory.");
    }

    private sealed class CliException : Exception
    {
        public CliException(string message)
            : base(message)
        {
        }
    }

    private static class OptionParser
    {
        public static Dictionary<string, string?> Parse(string[] args)
        {
            var result = new Dictionary<string, string?>(StringComparer.OrdinalIgnoreCase);

            for (var index = 0; index < args.Length; index++)
            {
                var token = args[index];
                if (!token.StartsWith("--", StringComparison.Ordinal))
                {
                    continue;
                }

                var trimmed = token.Substring(2);
                string optionName;
                string? optionValue;

                var equalsIndex = trimmed.IndexOf('=');
                if (equalsIndex >= 0)
                {
                    optionName = trimmed[..equalsIndex];
                    optionValue = trimmed[(equalsIndex + 1)..];
                }
                else
                {
                    optionName = trimmed;
                    if (index + 1 < args.Length && !args[index + 1].StartsWith("--", StringComparison.Ordinal))
                    {
                        optionValue = args[index + 1];
                        index++;
                    }
                    else
                    {
                        optionValue = null;
                    }
                }

                if (string.IsNullOrWhiteSpace(optionName))
                {
                    continue;
                }

                result[optionName] = optionValue;
            }

            return result;
        }
    }
}
