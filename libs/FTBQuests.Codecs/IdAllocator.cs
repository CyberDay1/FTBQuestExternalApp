using System.Collections.Generic;

namespace FTBQuests.Codecs;

public sealed class IdAllocator
{
    private readonly HashSet<long> used = new();

    public void Register(long existing)
    {
        used.Add(existing);
    }

    public long NextId()
    {
        long id = 1;
        while (used.Contains(id))
        {
            id++;
        }

        used.Add(id);
        return id;
    }
}
