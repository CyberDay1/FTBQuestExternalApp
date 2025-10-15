// <copyright file="GridConstants.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

namespace FTBQuestEditor.WinUI.ViewModels;

/// <summary>
/// Provides constants that match the in-game quest grid sizing rules.
/// The values are documented in <c>docs/parity_matrix.md</c>.
/// </summary>
public static class GridConstants
{
    /// <summary>
    /// Size of a single grid cell in pixels.
    /// </summary>
    public const double CellSize = 32d;

    /// <summary>
    /// Scaling factor applied to quest icons relative to their base asset.
    /// </summary>
    public const double IconScale = 0.85d;

    /// <summary>
    /// Space in pixels between adjacent grid cells.
    /// </summary>
    public const double Spacing = 4d;

    /// <summary>
    /// Size of a quest icon including grid spacing.
    /// </summary>
    public static double CellWithSpacing => CellSize + Spacing;

    /// <summary>
    /// Calculates a snapped coordinate along the grid.
    /// </summary>
    public static double Snap(double value)
    {
        if (double.IsNaN(value) || double.IsInfinity(value))
        {
            return 0d;
        }

        return Math.Round(value / CellSize, MidpointRounding.AwayFromZero) * CellSize;
    }
}
