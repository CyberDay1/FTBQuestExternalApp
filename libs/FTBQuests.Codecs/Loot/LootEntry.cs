// <copyright file="LootEntry.cs" company="CyberDay1">// Copyright (c) CyberDay1. All rights reserved.// </copyright>using FTBQuests.Codecs.Model;
using FTBQuests.Core.Model;

using FTBQuests.Assets;

namespace FTBQuests.Codecs.Loot;
/// <summary>/// Represents a single entry in a loot table./// </summary>/// <param name="Id">The identifier of the item to award.</param>/// <param name="Weight">The chance weight for the entry.</param>/// <param name="CountMin">The minimum quantity to grant.</param>/// <param name="CountMax">The maximum quantity to grant.</param>/// <param name="Conditions">Optional JSON conditions applied to the entry.</param>public sealed record LootEntry(    Identifier Id,    int Weight,    int CountMin,    int CountMax,    string? Conditions = null);
