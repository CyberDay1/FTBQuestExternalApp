using FTBQuests.Validation;
using FTBQuests.Assets;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace FTBQuestEditor.WinUI.Views;

public sealed partial class QuestCard : UserControl
{
    public QuestCard()
    {
        InitializeComponent();
        DataContextChanged += OnDataContextChanged;
    }

    private void OnDataContextChanged(FrameworkElement sender, DataContextChangedEventArgs args)
    {
        Bindings.Update();
    }
}
