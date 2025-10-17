namespace FTBQuests.Core.Model;

public sealed class Identifier
{
    public string Namespace { get; }
    public string Path { get; }

    public Identifier(string ns, string path)
    {
        Namespace = ns;
        Path = path;
    }

    public override string ToString() => $"{Namespace}:{Path}";
    public override int GetHashCode() => HashCode.Combine(Namespace, Path);
    public override bool Equals(object? obj)
        => obj is Identifier other &&
           Namespace == other.Namespace &&
           Path == other.Path;
}
