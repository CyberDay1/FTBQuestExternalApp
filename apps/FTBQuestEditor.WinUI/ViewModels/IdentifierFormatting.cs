using System;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels;

internal static class IdentifierFormatting
{
    public static string ToDisplayString(Identifier identifier)
    {
        var value = identifier.Value;
        return string.IsNullOrEmpty(value) ? string.Empty : value;
    }

    public static bool TryParse(string? text, out Identifier identifier)
    {
        if (string.IsNullOrWhiteSpace(text))
        {
            identifier = default;
            return false;
        }

        if (!Identifier.IsValid(text))
        {
            identifier = default;
            return false;
        }

        identifier = new Identifier(text!);
        return true;
    }
}
