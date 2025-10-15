using System;
using System.Collections.Generic;
using System.Linq;
using FTBQuestExternalApp.Codecs.Model;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace FTBQuestExternalApp.Codecs.Serialization;

public class QuestConverter : JsonConverter<Quest>
{
    private static readonly string[] DefaultPropertyOrder =
    {
        "id",
        "title",
        "subtitle",
        "icon",
        "x",
        "y",
        "page",
        "dependencies",
        "tasks",
        "rewards",
    };

    private static readonly string[] DefaultTaskPropertyOrder = { "type" };

    private static readonly string[] DefaultRewardPropertyOrder = { "type" };

    private static readonly IReadOnlyDictionary<string, Func<TaskBase>> TaskFactories =
        new Dictionary<string, Func<TaskBase>>(StringComparer.OrdinalIgnoreCase)
        {
            ["item"] = () => new ItemTask(),
            ["advancement"] = () => new AdvancementTask(),
            ["kill"] = () => new KillTask(),
            ["location"] = () => new LocationTask(),
            ["xp"] = () => new XpTask(),
            ["nbt"] = () => new NbtTask(),
            ["command"] = () => new CommandTask(),
            ["custom"] = () => new CustomTask(),
        };

    private static readonly IReadOnlyDictionary<string, Func<RewardBase>> RewardFactories =
        new Dictionary<string, Func<RewardBase>>(StringComparer.OrdinalIgnoreCase)
        {
            ["item"] = () => new ItemReward(),
            ["loot"] = () => new LootReward(),
            ["xp"] = () => new XpReward(),
            ["command"] = () => new CommandReward(),
            ["custom"] = () => new CustomReward(),
        };

    private static readonly string[] IconKeys = { "icon", "iconId", "icon_id" };

    public override Quest? ReadJson(JsonReader reader, Type objectType, Quest? existingValue, bool hasExistingValue, JsonSerializer serializer)
    {
        if (reader.TokenType == JsonToken.Null)
        {
            return null;
        }

        var jobject = JObject.Load(reader);
        var quest = existingValue ?? new Quest();
        var properties = jobject.Properties().ToList();

        quest.SetPropertyOrder(properties.Select(p => p.Name));
        quest.Tasks.Clear();
        quest.Rewards.Clear();
        quest.Dependencies.Clear();
        quest.Extra.Extra.Clear();
        quest.Title = string.Empty;
        quest.Subtitle = null;
        quest.IconId = null;
        quest.PositionX = 0;
        quest.PositionY = 0;
        quest.Page = 0;

        foreach (var property in properties)
        {
            switch (property.Name)
            {
                case "id":
                    quest.Id = property.Value.Type == JTokenType.Null
                        ? Guid.Empty
                        : property.Value.ToObject<Guid>(serializer);
                    break;
                case "title":
                    quest.Title = property.Value.Type == JTokenType.Null
                        ? string.Empty
                        : property.Value.Value<string>() ?? string.Empty;
                    break;
                case "subtitle":
                    quest.Subtitle = property.Value.Type == JTokenType.Null
                        ? null
                        : property.Value.Value<string>();
                    break;
                case "icon":
                case "iconId":
                case "icon_id":
                    if (property.Value.Type == JTokenType.Null)
                    {
                        quest.IconId = null;
                    }
                    else
                    {
                        var iconValue = property.Value.Value<string>();
                        quest.IconId = iconValue is null ? null : new Identifier(iconValue);
                    }

                    break;
                case "x":
                    quest.PositionX = property.Value.Type == JTokenType.Null
                        ? 0
                        : property.Value.Value<int>();
                    break;
                case "y":
                    quest.PositionY = property.Value.Type == JTokenType.Null
                        ? 0
                        : property.Value.Value<int>();
                    break;
                case "page":
                    quest.Page = property.Value.Type == JTokenType.Null
                        ? 0
                        : property.Value.Value<int>();
                    break;
                case "dependencies":
                    if (property.Value.Type == JTokenType.Null)
                    {
                        quest.Dependencies.Clear();
                    }
                    else
                    {
                        var dependencies = property.Value.ToObject<List<Guid>>(serializer) ?? new List<Guid>();
                        quest.Dependencies.Clear();
                        quest.Dependencies.AddRange(dependencies);
                    }

                    break;
                case "tasks":
                    quest.Tasks.Clear();

                    if (property.Value.Type != JTokenType.Null)
                    {
                        foreach (var taskToken in (JArray)property.Value)
                        {
                            var task = DeserializeTask(taskToken, serializer);
                            if (task is not null)
                            {
                                quest.Tasks.Add(task);
                            }
                        }
                    }

                    break;
                case "rewards":
                    quest.Rewards.Clear();

                    if (property.Value.Type != JTokenType.Null)
                    {
                        foreach (var rewardToken in (JArray)property.Value)
                        {
                            var reward = DeserializeReward(rewardToken, serializer);
                            if (reward is not null)
                            {
                                quest.Rewards.Add(reward);
                            }
                        }
                    }

                    break;
                default:
                    quest.Extra.Add(property.Name, property.Value.DeepClone());
                    break;
            }
        }

        return quest;
    }

    public override void WriteJson(JsonWriter writer, Quest? value, JsonSerializer serializer)
    {
        if (value is null)
        {
            writer.WriteNull();
            return;
        }

        var knownTokens = new Dictionary<string, JToken>(StringComparer.Ordinal)
        {
            ["id"] = JToken.FromObject(value.Id, serializer),
        };

        if (!string.IsNullOrEmpty(value.Title) || value.PropertyOrder.Contains("title"))
        {
            knownTokens["title"] = JToken.FromObject(value.Title, serializer);
        }

        if (value.Subtitle is not null)
        {
            knownTokens["subtitle"] = JToken.FromObject(value.Subtitle, serializer);
        }
        else if (value.PropertyOrder.Contains("subtitle"))
        {
            knownTokens["subtitle"] = JValue.CreateNull();
        }

        var iconKey = ResolveKey(value.PropertyOrder, IconKeys);
        if (value.IconId is Identifier iconId)
        {
            knownTokens[iconKey ?? "icon"] = JToken.FromObject(iconId, serializer);
        }
        else if (iconKey is not null)
        {
            knownTokens[iconKey] = JValue.CreateNull();
        }

        if (value.PropertyOrder.Contains("x") || value.PositionX != 0)
        {
            knownTokens["x"] = JToken.FromObject(value.PositionX, serializer);
        }

        if (value.PropertyOrder.Contains("y") || value.PositionY != 0)
        {
            knownTokens["y"] = JToken.FromObject(value.PositionY, serializer);
        }

        if (value.PropertyOrder.Contains("page") || value.Page != 0)
        {
            knownTokens["page"] = JToken.FromObject(value.Page, serializer);
        }

        if (value.PropertyOrder.Contains("dependencies") || value.Dependencies.Count > 0)
        {
            knownTokens["dependencies"] = JToken.FromObject(value.Dependencies, serializer);
        }

        if (value.PropertyOrder.Contains("tasks") || value.Tasks.Count > 0)
        {
            knownTokens["tasks"] = SerializeTasks(serializer, value.Tasks);
        }

        if (value.PropertyOrder.Contains("rewards") || value.Rewards.Count > 0)
        {
            knownTokens["rewards"] = SerializeRewards(serializer, value.Rewards);
        }

        var orderedKeys = value.PropertyOrder.Count > 0 ? value.PropertyOrder : DefaultPropertyOrder;
        var written = new HashSet<string>(StringComparer.Ordinal);
        var jobject = new JObject();

        foreach (var key in orderedKeys)
        {
            if (written.Contains(key))
            {
                continue;
            }

            if (knownTokens.TryGetValue(key, out var knownToken))
            {
                jobject.Add(key, knownToken);
                written.Add(key);
                continue;
            }

            if (value.Extra.Extra.TryGetValue(key, out var extraToken))
            {
                jobject.Add(key, extraToken.DeepClone());
                written.Add(key);
            }
        }

        foreach (var key in DefaultPropertyOrder)
        {
            if (written.Contains(key))
            {
                continue;
            }

            if (knownTokens.TryGetValue(key, out var knownToken))
            {
                jobject.Add(key, knownToken);
                written.Add(key);
            }
        }

        foreach (var kvp in value.Extra.Extra)
        {
            if (written.Contains(kvp.Key))
            {
                continue;
            }

            jobject.Add(kvp.Key, kvp.Value.DeepClone());
            written.Add(kvp.Key);
        }

        jobject.WriteTo(writer);
    }

    private static string? ResolveKey(IList<string> propertyOrder, IReadOnlyList<string> candidates)
    {
        foreach (var candidate in candidates)
        {
            if (propertyOrder.Contains(candidate))
            {
                return candidate;
            }
        }

        return null;
    }

    private static TaskBase CreateTask(string? typeId)
    {
        if (!string.IsNullOrEmpty(typeId) && TaskFactories.TryGetValue(typeId, out var factory))
        {
            var knownTask = factory();
            knownTask.SetTypeId(typeId);
            return knownTask;
        }

        var fallback = string.IsNullOrEmpty(typeId) ? "custom" : typeId;
        var unknownTask = new UnknownTask(fallback);
        unknownTask.SetTypeId(typeId);
        return unknownTask;
    }

    private static RewardBase CreateReward(string? typeId)
    {
        if (!string.IsNullOrEmpty(typeId) && RewardFactories.TryGetValue(typeId, out var factory))
        {
            var knownReward = factory();
            knownReward.SetTypeId(typeId);
            return knownReward;
        }

        var fallback = string.IsNullOrEmpty(typeId) ? "custom" : typeId;
        var unknownReward = new UnknownReward(fallback);
        unknownReward.SetTypeId(typeId);
        return unknownReward;
    }

    private static ITask? DeserializeTask(JToken token, JsonSerializer serializer)
    {
        if (token is null || token.Type == JTokenType.Null)
        {
            return null;
        }

        if (token is not JObject taskObject)
        {
            var fallback = new UnknownTask("custom");
            fallback.SetTypeId(null);
            fallback.SetPropertyOrder(Array.Empty<string>());
            fallback.Extra.Extra.Clear();
            fallback.Extra.Add("value", token.DeepClone());
            return fallback;
        }

        var properties = taskObject.Properties().ToList();
        var typeProperty = properties.FirstOrDefault(p => string.Equals(p.Name, "type", StringComparison.OrdinalIgnoreCase));
        var discriminator = typeProperty?.Value?.Value<string>();
        var task = CreateTask(discriminator);

        task.SetPropertyOrder(properties.Select(p => p.Name));
        task.Extra.Extra.Clear();

        foreach (var property in properties)
        {
            if (string.Equals(property.Name, "type", StringComparison.OrdinalIgnoreCase))
            {
                continue;
            }

            task.Extra.Add(property.Name, property.Value.DeepClone());
        }

        return task;
    }

    private static IReward? DeserializeReward(JToken token, JsonSerializer serializer)
    {
        if (token is null || token.Type == JTokenType.Null)
        {
            return null;
        }

        if (token is not JObject rewardObject)
        {
            var fallback = new UnknownReward("custom");
            fallback.SetTypeId(null);
            fallback.SetPropertyOrder(Array.Empty<string>());
            fallback.Extra.Extra.Clear();
            fallback.Extra.Add("value", token.DeepClone());
            return fallback;
        }

        var properties = rewardObject.Properties().ToList();
        var typeProperty = properties.FirstOrDefault(p => string.Equals(p.Name, "type", StringComparison.OrdinalIgnoreCase));
        var discriminator = typeProperty?.Value?.Value<string>();
        var reward = CreateReward(discriminator);

        reward.SetPropertyOrder(properties.Select(p => p.Name));
        reward.Extra.Extra.Clear();

        foreach (var property in properties)
        {
            if (string.Equals(property.Name, "type", StringComparison.OrdinalIgnoreCase))
            {
                continue;
            }

            reward.Extra.Add(property.Name, property.Value.DeepClone());
        }

        return reward;
    }

    private static JToken SerializeTasks(JsonSerializer serializer, IEnumerable<ITask> tasks)
    {
        var array = new JArray();

        foreach (var task in tasks)
        {
            array.Add(SerializeTask(serializer, task));
        }

        return array;
    }

    private static JToken SerializeRewards(JsonSerializer serializer, IEnumerable<IReward> rewards)
    {
        var array = new JArray();

        foreach (var reward in rewards)
        {
            array.Add(SerializeReward(serializer, reward));
        }

        return array;
    }

    private static JToken SerializeTask(JsonSerializer serializer, ITask task)
    {
        if (task is not TaskBase taskBase)
        {
            return JToken.FromObject(task, serializer);
        }

        var knownTokens = new Dictionary<string, JToken>(StringComparer.Ordinal)
        {
            ["type"] = JToken.FromObject(taskBase.TypeId, serializer),
        };

        var orderedKeys = taskBase.PropertyOrder.Count > 0 ? taskBase.PropertyOrder : DefaultTaskPropertyOrder;
        var written = new HashSet<string>(StringComparer.Ordinal);
        var jobject = new JObject();

        foreach (var key in orderedKeys)
        {
            if (written.Contains(key))
            {
                continue;
            }

            if (knownTokens.TryGetValue(key, out var knownToken))
            {
                jobject.Add(key, knownToken);
                written.Add(key);
                continue;
            }

            if (taskBase.Extra.Extra.TryGetValue(key, out var extraToken))
            {
                jobject.Add(key, extraToken.DeepClone());
                written.Add(key);
            }
        }

        foreach (var key in DefaultTaskPropertyOrder)
        {
            if (written.Contains(key))
            {
                continue;
            }

            if (knownTokens.TryGetValue(key, out var knownToken))
            {
                jobject.Add(key, knownToken);
                written.Add(key);
            }
        }

        foreach (var kvp in taskBase.Extra.Extra)
        {
            if (written.Contains(kvp.Key))
            {
                continue;
            }

            jobject.Add(kvp.Key, kvp.Value.DeepClone());
            written.Add(kvp.Key);
        }

        return jobject;
    }

    private static JToken SerializeReward(JsonSerializer serializer, IReward reward)
    {
        if (reward is not RewardBase rewardBase)
        {
            return JToken.FromObject(reward, serializer);
        }

        var knownTokens = new Dictionary<string, JToken>(StringComparer.Ordinal)
        {
            ["type"] = JToken.FromObject(rewardBase.TypeId, serializer),
        };

        var orderedKeys = rewardBase.PropertyOrder.Count > 0 ? rewardBase.PropertyOrder : DefaultRewardPropertyOrder;
        var written = new HashSet<string>(StringComparer.Ordinal);
        var jobject = new JObject();

        foreach (var key in orderedKeys)
        {
            if (written.Contains(key))
            {
                continue;
            }

            if (knownTokens.TryGetValue(key, out var knownToken))
            {
                jobject.Add(key, knownToken);
                written.Add(key);
                continue;
            }

            if (rewardBase.Extra.Extra.TryGetValue(key, out var extraToken))
            {
                jobject.Add(key, extraToken.DeepClone());
                written.Add(key);
            }
        }

        foreach (var key in DefaultRewardPropertyOrder)
        {
            if (written.Contains(key))
            {
                continue;
            }

            if (knownTokens.TryGetValue(key, out var knownToken))
            {
                jobject.Add(key, knownToken);
                written.Add(key);
            }
        }

        foreach (var kvp in rewardBase.Extra.Extra)
        {
            if (written.Contains(kvp.Key))
            {
                continue;
            }

            jobject.Add(kvp.Key, kvp.Value.DeepClone());
            written.Add(kvp.Key);
        }

        return jobject;
    }
}
