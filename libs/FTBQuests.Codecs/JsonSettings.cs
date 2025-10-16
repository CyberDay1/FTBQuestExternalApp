using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="JsonSettings.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using FTBQuests.Codecs.Serialization;
using Newtonsoft.Json;

namespace FTBQuests.Codecs;

public static class JsonSettings
{
    public static JsonSerializerSettings Create()
    {
        var settings = new JsonSerializerSettings();
        settings.Converters.Add(new ChapterConverter());
        settings.Converters.Add(new QuestConverter());
        return settings;
    }
}

