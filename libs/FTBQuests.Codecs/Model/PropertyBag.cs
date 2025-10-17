using System;
using System.Collections.Generic;
using Newtonsoft.Json.Linq;

namespace FTBQuests.Codecs.Model;

public sealed class PropertyBag
{
    public Dictionary<string, JToken> Extra { get; } = new(StringComparer.Ordinal);

    public void Add(string key, JToken value)
    {
        Extra[key] = value;
    }

    public bool TryGetValue(string key, out JToken? value)
    {
        return Extra.TryGetValue(key, out value);
    }
}
