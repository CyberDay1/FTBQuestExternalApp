// <copyright file="ValidationSeverity.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

namespace FTBQuests.Validation;

/// <summary>
/// Represents the severity of a validation issue discovered in a quest pack.
/// </summary>
public enum ValidationSeverity
{
    /// <summary>
    /// Informational message that does not indicate a problem.
    /// </summary>
    Info,

    /// <summary>
    /// Potential problem that may require user attention.
    /// </summary>
    Warning,

    /// <summary>
    /// Critical problem that must be fixed before the pack can be used.
    /// </summary>
    Error,
}
