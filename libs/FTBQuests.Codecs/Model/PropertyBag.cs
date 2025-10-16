using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="PropertyBag.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System.Collections.Generic;
using Newtonsoft.Json.Linq;

namespace FTBQuests.Codecs.Model;

public class PropertyBag
{
    public Dictionary<string, JToken> Extra { get; } = new();

    public void Add(string key, JToken value)
    {
        Extra[key] = value;
    }

    public bool TryGetValue(string key, out JToken? value) => Extra.TryGetValue(key, out value);
}

