// <copyright file="IValidator.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System.Collections.Generic;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuests.Validation;

/// <summary>
/// Defines a validation routine that inspects quest packs for inconsistencies.
/// </summary>
public interface IValidator
{
    /// <summary>
    /// Validates the supplied quest pack and returns any discovered issues.
    /// </summary>
    /// <param name="questPack">The quest pack to inspect.</param>
    /// <returns>The sequence of validation issues discovered during the inspection.</returns>
    IEnumerable<ValidationIssue> Validate(QuestPack questPack);
}
