// <copyright file="IdAllocator.cs" company="CyberDay1">// Copyright (c) CyberDay1. All rights reserved.// </copyright>
using FTBQuests.Core.Model;

using FTBQuests.Assets;

namespace FTBQuests.Codecs;
public sealed class IdAllocator{    private readonly System.Collections.Generic.HashSet<long> _used = new();    public void Register(long existing) => _used.Add(existing);    public long NextId()    {        long id = 1;        while (_used.Contains(id))        {            id++;        }        _used.Add(id);        return id;    }}
