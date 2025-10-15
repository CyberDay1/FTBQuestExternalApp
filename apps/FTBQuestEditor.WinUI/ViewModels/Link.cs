using System;

namespace FTBQuestEditor.WinUI.ViewModels;

/// <summary>
/// Represents a dependency link between two quests.
/// </summary>
/// <param name="SourceId">The quest that must be completed first.</param>
/// <param name="TargetId">The quest that depends on the source.</param>
public sealed record Link(Guid SourceId, Guid TargetId);
