namespace FTBQuests.IO;

public sealed class ImportOptions
{
    public string RootPath { get; init; } = string.Empty;

    public bool PreferConfigPath { get; init; } = true;
}
