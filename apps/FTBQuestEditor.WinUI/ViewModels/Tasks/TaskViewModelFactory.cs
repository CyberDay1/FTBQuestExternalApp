using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="TaskViewModelFactory.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using FTBQuestEditor.WinUI.ViewModels.Tasks;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels.Tasks;

internal static class TaskViewModelFactory
{
    public static TaskPropertiesViewModel Create(ITask task, string pathPrefix)
    {
        return task switch
        {
            ItemTask itemTask => new ItemTaskPropertiesViewModel(itemTask, pathPrefix),
            AdvancementTask advancementTask => new AdvancementTaskPropertiesViewModel(advancementTask, pathPrefix),
            KillTask killTask => new KillTaskPropertiesViewModel(killTask, pathPrefix),
            LocationTask locationTask => new LocationTaskPropertiesViewModel(locationTask, pathPrefix),
            XpTask xpTask => new XpTaskPropertiesViewModel(xpTask, pathPrefix),
            NbtTask nbtTask => new NbtTaskPropertiesViewModel(nbtTask, pathPrefix),
            CommandTask commandTask => new CommandTaskPropertiesViewModel(commandTask, pathPrefix),
            CustomTask customTask => new CustomTaskPropertiesViewModel(customTask, pathPrefix),
            UnknownTask unknownTask => new UnknownTaskPropertiesViewModel(unknownTask, pathPrefix),
            _ => new ExternalTaskPropertiesViewModel(task, pathPrefix),
        };
    }
}
