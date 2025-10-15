using System;
using System.Text.RegularExpressions;

namespace FTBQuestExternalApp.Codecs.Model;

public readonly struct Identifier : IEquatable<Identifier>
{
    private static readonly Regex Pattern = new("^[a-z0-9_.-]+:[a-z0-9_./-]+$", RegexOptions.Compiled | RegexOptions.CultureInvariant);

    public Identifier(string value)
    {
        if (!IsValid(value))
        {
            throw new ArgumentException($"Identifier must be in the form 'namespace:path'. Value: '{value}'", nameof(value));
        }

        Value = value!;
    }

    public string Value { get; }

    public static bool IsValid(string? value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            return false;
        }

        return Pattern.IsMatch(value);
    }

    public override string ToString() => Value;

    public bool Equals(Identifier other) => string.Equals(Value, other.Value, StringComparison.Ordinal);

    public override bool Equals(object? obj) => obj is Identifier other && Equals(other);

    public override int GetHashCode() => Value.GetHashCode(StringComparison.Ordinal);

    public static implicit operator Identifier(string value) => new(value);

    public static implicit operator string(Identifier identifier) => identifier.Value;
}
