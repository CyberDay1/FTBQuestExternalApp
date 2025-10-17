// <copyright file="VanillaRegistrySeeder.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>
using System;
using System.Collections.Generic;
using System.IO;

using FTBQuests.Core.Model;

using FTBQuests.Assets;

using FTBQuests.Registry.Model;

namespace FTBQuests.Registry.Seed;

public static class VanillaRegistrySeeder
{
    public static void EnsureBaseItems(RegistryDatabase db, string seedPath = "data/minecraft_registry/vanilla_items.json")
    {
        ArgumentNullException.ThrowIfNull(db);

        string resolvedPath = Path.IsPathRooted(seedPath) ? seedPath : Path.Combine(AppContext.BaseDirectory, seedPath);
        if (!File.Exists(resolvedPath))
        {
            return;
        }

        string json = File.ReadAllText(resolvedPath);
        List<RegistryItem> baseItems = Newtonsoft.Json.JsonConvert.DeserializeObject<List<RegistryItem>>(json) ?? new List<RegistryItem>();
        foreach (RegistryItem item in baseItems)
        {
            db.AddIfMissing(item);
        }
    }
}
