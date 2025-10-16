using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="App.xaml.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.IO;
using Microsoft.UI.Xaml;

namespace FTBQuestEditor.WinUI;

public partial class App : Application
{
    private Window? window;

    public App()
    {
        InitializeComponent();
        InitializePortableStorage();
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        window ??= new MainWindow();
        window.Activate();
    }

    private static void InitializePortableStorage()
    {
        string baseDir = AppContext.BaseDirectory;
        Directory.SetCurrentDirectory(baseDir);

        string dataDir = Path.Combine(baseDir, "portable_data");
        Directory.CreateDirectory(dataDir);

        string logsDir = Path.Combine(baseDir, "portable_logs");
        Directory.CreateDirectory(logsDir);

        Environment.SetEnvironmentVariable("FTBQUESTEDITOR_DATA", dataDir, EnvironmentVariableTarget.Process);
        Environment.SetEnvironmentVariable("FTBQUESTEDITOR_LOGS", logsDir, EnvironmentVariableTarget.Process);
    }
}
