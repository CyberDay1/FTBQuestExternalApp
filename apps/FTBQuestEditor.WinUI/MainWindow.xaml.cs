using FTBQuestEditor.WinUI.ViewModels;
using Microsoft.UI.Xaml;

namespace FTBQuestEditor.WinUI;

public sealed partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();
        ViewModel = QuestGraphViewModel.CreateSample();
        DataContext = ViewModel;
    }

    public QuestGraphViewModel ViewModel { get; }
}
