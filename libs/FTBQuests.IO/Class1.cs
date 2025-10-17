using FTBQuests.Core.Validation;
using FTBQuests.Codecs;
using FTBQuests.Core.Model;

using FTBQuests.Codecs;
using FTBQuests.Core.Model;



using FTBQuests.Assets;
// <copyright file="Class1.cs" company="CyberDay1">
// Copyright (c) CyberDay1. All rights reserved.
// </copyright>



namespace FTBQuests.IO;

/// <summary>
/// Provides extension points for quest data input and output.
/// </summary>
public sealed class QuestDataIo
{
    /// <summary>
    /// Initializes a new instance of the <see cref="QuestDataIo"/> class.
    /// </summary>
    /// <param name="registry">The codec registry that supports serialization.</param>
    public QuestDataIo(CodecRegistry registry)
    {
        Registry = registry ?? throw new ArgumentNullException(nameof(registry));
    }

    /// <summary>
    /// Gets the codec registry used for serialization.
    /// </summary>
    public CodecRegistry Registry { get; }
}



