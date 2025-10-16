using FTBQuests.Validation;
using FTBQuests.Assets;
using System;
using System.Collections.Generic;
using FTBQuestExternalApp.Codecs.Model;

namespace FTBQuestEditor.WinUI.ViewModels;

/// <summary>
/// View model that wraps a quest and exposes dependency helpers.
/// </summary>
public sealed class QuestNodeViewModel : ObservableObject
{
    public QuestNodeViewModel(Quest quest)
    {
        Quest = quest ?? throw new ArgumentNullException(nameof(quest));
    }

    public Quest Quest { get; }

    public Guid Id => Quest.Id;

    public string Title
    {
        get => Quest.Title;
        set
        {
            if (Quest.Title != value)
            {
                Quest.Title = value;
                OnPropertyChanged();
            }
        }
    }

    public string? Subtitle
    {
        get => Quest.Subtitle;
        set
        {
            if (Quest.Subtitle != value)
            {
                Quest.Subtitle = value;
                OnPropertyChanged();
            }
        }
    }

    public int PositionX
    {
        get => Quest.PositionX;
        set
        {
            if (Quest.PositionX != value)
            {
                Quest.PositionX = value;
                OnPropertyChanged();
            }
        }
    }

    public int PositionY
    {
        get => Quest.PositionY;
        set
        {
            if (Quest.PositionY != value)
            {
                Quest.PositionY = value;
                OnPropertyChanged();
            }
        }
    }

    public IReadOnlyList<Guid> Dependencies => Quest.Dependencies;

    public bool HasDependency(Guid questId)
    {
        return Quest.Dependencies.Contains(questId);
    }

    public bool AddDependency(Guid questId)
    {
        if (questId == Id)
        {
            return false;
        }

        if (Quest.Dependencies.Contains(questId))
        {
            return false;
        }

        Quest.Dependencies.Add(questId);
        OnPropertyChanged(nameof(Dependencies));
        return true;
    }

    public bool RemoveDependency(Guid questId)
    {
        if (!Quest.Dependencies.Remove(questId))
        {
            return false;
        }

        OnPropertyChanged(nameof(Dependencies));
        return true;
    }

    public override string ToString()
    {
        return Quest.Title;
    }
}
