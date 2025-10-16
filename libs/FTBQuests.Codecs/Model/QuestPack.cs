using System.Collections.Generic;
using System.Text.Json.Serialization;

namespace FTBQuestExternalApp.Codecs.Model
{
    public class QuestPack
    {
        [JsonPropertyName("chapters")]
        public List<QuestChapter> Chapters { get; set; } = new();

        [JsonPropertyName("id")]
        public string Id { get; set; } = string.Empty;

        [JsonPropertyName("version")]
        public int Version { get; set; } = 1;

        [JsonPropertyName("author")]
        public string Author { get; set; } = string.Empty;

        [JsonPropertyName("description")]
        public string Description { get; set; } = string.Empty;

        [JsonPropertyName("rewards")]
        public List<QuestReward> Rewards { get; set; } = new();
    }
}
