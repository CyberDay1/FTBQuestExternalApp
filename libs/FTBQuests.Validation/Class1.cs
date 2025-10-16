using FTBQuests.Validation;
using FTBQuests.Assets;
// <copyright file="Class1.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>

using FTBQuests.Codecs;
using FTBQuests.IO;

namespace FTBQuests.Validation;

/// <summary>
/// Provides hooks for validating quest data structures.
/// </summary>
public sealed class QuestValidator
{
    private readonly CodecRegistry codecRegistry;
    private readonly QuestDataIo questDataIo;

    /// <summary>
    /// Initializes a new instance of the <see cref="QuestValidator"/> class.
    /// </summary>
    /// <param name="codecRegistry">Codec registry for serialization support.</param>
    /// <param name="questDataIo">I/O helper for quest files.</param>
    public QuestValidator(CodecRegistry codecRegistry, QuestDataIo questDataIo)
    {
        this.codecRegistry = codecRegistry ?? throw new ArgumentNullException(nameof(codecRegistry));
        this.questDataIo = questDataIo ?? throw new ArgumentNullException(nameof(questDataIo));
    }

    /// <summary>
    /// Gets the codec registry used by the validator.
    /// </summary>
    public CodecRegistry CodecRegistry => codecRegistry;

    /// <summary>
    /// Gets the quest I/O helper used by the validator.
    /// </summary>
    public QuestDataIo QuestDataIo => questDataIo;
}
