using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="ParityMatrixDocumentationTests.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using FTBQuestEditor.WinUI.ViewModels;
using Xunit;

namespace FTBQuests.Tests;

public static class ParityMatrixDocumentationTests
{
    [Fact]
    public static void GridConstantsMatchDocumentedValues()
    {
        var parityPath = GetParityMatrixPath();
        var documentedValues = ParseConstantTable(parityPath);

        Assert.True(documentedValues.TryGetValue("CellSize", out var cellSize));
        Assert.Equal(GridConstants.CellSize, cellSize);

        Assert.True(documentedValues.TryGetValue("IconScale", out var iconScale));
        Assert.Equal(GridConstants.IconScale, iconScale);

        Assert.True(documentedValues.TryGetValue("Spacing", out var spacing));
        Assert.Equal(GridConstants.Spacing, spacing);

        Assert.True(documentedValues.TryGetValue("CellWithSpacing", out var cellWithSpacing));
        Assert.Equal(GridConstants.CellWithSpacing, cellWithSpacing);
        Assert.Equal(GridConstants.CellSize + GridConstants.Spacing, cellWithSpacing);
    }

    private static string GetParityMatrixPath()
    {
        var directory = new DirectoryInfo(AppContext.BaseDirectory);

        while (directory is not null && !File.Exists(Path.Combine(directory.FullName, "FTBQuests.sln")))
        {
            directory = directory.Parent;
        }

        if (directory is null)
        {
            throw new InvalidOperationException("Unable to locate repository root for parity documentation.");
        }

        return Path.Combine(directory.FullName, "docs", "parity_matrix.md");
    }

    private static Dictionary<string, double> ParseConstantTable(string path)
    {
        var result = new Dictionary<string, double>(StringComparer.OrdinalIgnoreCase);

        foreach (var line in File.ReadLines(path))
        {
            var trimmed = line.Trim();
            if (string.IsNullOrEmpty(trimmed) || trimmed.StartsWith("|-", StringComparison.Ordinal) || trimmed.StartsWith("|--", StringComparison.Ordinal))
            {
                continue;
            }

            if (trimmed.StartsWith("| Constant", StringComparison.OrdinalIgnoreCase))
            {
                continue;
            }

            var cells = line.Split('|', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
            if (cells.Length < 2)
            {
                continue;
            }

            var name = cells[0];
            var valueText = cells.Length > 1 ? cells[1] : null;
            if (string.IsNullOrEmpty(name) || string.IsNullOrEmpty(valueText))
            {
                continue;
            }

            if (double.TryParse(valueText, NumberStyles.Float, CultureInfo.InvariantCulture, out var value))
            {
                result[name] = value;
            }
        }

        return result;
    }
}

