// <copyright file="IdAllocatorTests.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using System;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using FTBQuestExternalApp.Codecs;
using FTBQuests.IO;
using Xunit;

namespace FTBQuests.Tests;

public class IdAllocatorTests
{
    [Fact]
    public void NextId_ReturnsSequentialValuesWhenNoIdsRegistered()
    {
        var allocator = new IdAllocator();

        var ids = Enumerable.Range(0, 5).Select(_ => allocator.NextId()).ToArray();

        Assert.Equal(new long[] { 1, 2, 3, 4, 5 }, ids);
    }

    [Fact]
    public void NextId_SkipsRegisteredIds()
    {
        var allocator = new IdAllocator();
        allocator.Register(1);
        allocator.Register(2);
        allocator.Register(4);

        var nextIds = new long[]
        {
            allocator.NextId(),
            allocator.NextId(),
            allocator.NextId(),
        };

        Assert.Equal(new long[] { 3, 5, 6 }, nextIds);
    }

    [Fact]
    public async Task QuestPack_GetNextId_ContinuesAfterLoadedIds()
    {
        var loader = new QuestPackLoader();
        var pack = await loader.LoadAsync(GetFixturePath("RoundTripPack"));

        var generatedIds = new[]
        {
            pack.GetNextId(),
            pack.GetNextId(),
        };

        Assert.Equal(new long[] { 6, 7 }, generatedIds);
    }

    [Fact]
    public void QuestPackCreationHelpers_AssignUniqueIds()
    {
        var pack = new QuestPack();
        var chapter = pack.CreateChapter();
        var quest = pack.CreateQuest(chapter);

        Assert.Equal(1, chapter.Id);
        Assert.Equal(2, quest.Id);
    }

    private static string GetFixturePath(string name)
    {
        var baseDirectory = AppContext.BaseDirectory;
        return Path.GetFullPath(Path.Combine(baseDirectory, "Fixtures", name));
    }
}
