using FTBQuests.Core.Validation;
using FTBQuests.Codecs;
using FTBQuests.Core.Model;

using FTBQuests.Codecs;
using FTBQuests.Core.Model;




using FTBQuests.Assets;
// <copyright file="ImportOptions.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

namespace FTBQuests.IO;

public sealed class ImportOptions
{
    public string RootPath { get; init; } = string.Empty;

    public bool PreferConfigPath { get; init; } = true;
}



