// <copyright file="ValidationIssue.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

namespace FTBQuests.Validation;

/// <summary>
/// Represents a validation issue discovered while analyzing a quest pack.
/// </summary>
/// <param name="Severity">The severity of the issue.</param>
/// <param name="Path">The dotted JSON path to the failing value.</param>
/// <param name="Message">Human-readable description of the issue.</param>
/// <param name="Code">Stable machine-readable code describing the issue.</param>
public sealed record ValidationIssue(ValidationSeverity Severity, string Path, string Message, string Code);
