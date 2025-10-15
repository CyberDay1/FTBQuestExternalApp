// <copyright file="ValidationAwareViewModel.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.Collections.Generic;
using System.Linq;
using FTBQuests.Validation;

namespace FTBQuestEditor.WinUI.ViewModels.Infrastructure;

public abstract class ValidationAwareViewModel : ObservableObject
{
    private IReadOnlyList<ValidationIssue> currentIssues = Array.Empty<ValidationIssue>();

    protected ValidationAwareViewModel(string pathPrefix)
    {
        PathPrefix = pathPrefix ?? string.Empty;
    }

    public string PathPrefix { get; }

    protected IReadOnlyList<ValidationIssue> CurrentIssues => currentIssues;

    public void UpdateValidationIssues(IEnumerable<ValidationIssue> issues)
    {
        currentIssues = issues?
            .Where(IsIssueRelevant)
            .ToList() ?? Array.Empty<ValidationIssue>();

        OnValidationIssuesChanged();
    }

    protected string? GetIssueMessage(params string[] relativePaths)
    {
        foreach (var relativePath in relativePaths)
        {
            var issue = FindIssue(relativePath);
            if (issue is not null)
            {
                return issue.Message;
            }
        }

        return null;
    }

    protected static string? CombineMessages(params string?[] messages)
    {
        var parts = messages
            .Where(message => !string.IsNullOrWhiteSpace(message))
            .Select(message => message!.Trim())
            .ToArray();

        return parts.Length == 0 ? null : string.Join("\n", parts);
    }

    protected string BuildPath(string relativePath)
    {
        if (string.IsNullOrEmpty(relativePath))
        {
            return PathPrefix;
        }

        if (string.IsNullOrEmpty(PathPrefix))
        {
            return relativePath;
        }

        return string.Create(
            PathPrefix.Length + 1 + relativePath.Length,
            (PathPrefix: PathPrefix, RelativePath: relativePath),
            static (span, state) =>
            {
                state.PathPrefix.AsSpan().CopyTo(span);
                span[state.PathPrefix.Length] = '.';
                state.RelativePath.AsSpan().CopyTo(span[(state.PathPrefix.Length + 1)..]);
            });
    }

    protected ValidationIssue? FindIssue(string relativePath)
    {
        if (CurrentIssues.Count == 0)
        {
            return null;
        }

        var target = BuildPath(relativePath);
        return CurrentIssues.FirstOrDefault(issue => string.Equals(issue.Path, target, StringComparison.OrdinalIgnoreCase));
    }

    protected virtual bool IsIssueRelevant(ValidationIssue issue)
    {
        if (string.IsNullOrEmpty(PathPrefix))
        {
            return true;
        }

        if (string.IsNullOrEmpty(issue.Path))
        {
            return false;
        }

        if (string.Equals(issue.Path, PathPrefix, StringComparison.OrdinalIgnoreCase))
        {
            return true;
        }

        return issue.Path.StartsWith(PathPrefix + ".", StringComparison.OrdinalIgnoreCase)
            || issue.Path.StartsWith(PathPrefix + "[", StringComparison.OrdinalIgnoreCase);
    }

    protected abstract void OnValidationIssuesChanged();
}
