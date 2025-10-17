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
