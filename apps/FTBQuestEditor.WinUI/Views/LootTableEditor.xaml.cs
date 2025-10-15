using FTBQuestEditor.WinUI.ViewModels;
using Microsoft.UI.Xaml.Controls;

namespace FTBQuestEditor.WinUI.Views;

public sealed partial class LootTableEditor : Page
{
    public LootTableEditor()
    {
        InitializeComponent();
    }

    public LootTableEditorViewModel? ViewModel
    {
        get => DataContext as LootTableEditorViewModel;
        set => DataContext = value;
    }
}
