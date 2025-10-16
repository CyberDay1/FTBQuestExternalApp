using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="TaskPropertiesViewModel.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using FTBQuestEditor.WinUI.ViewModels.Infrastructure;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Tasks;

public abstract class TaskPropertiesViewModel : ValidationAwareViewModel
{
    protected TaskPropertiesViewModel(ITask task, string pathPrefix, string displayName)
        : base(pathPrefix)
    {
        Task = task;
        DisplayName = displayName;
    }

    public ITask Task { get; }

    public string DisplayName { get; }

    public string TypeId => Task.TypeId;
}
