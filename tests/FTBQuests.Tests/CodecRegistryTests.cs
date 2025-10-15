using FTBQuests.Codecs;
using Xunit;

namespace FTBQuests.Tests;

public class CodecRegistryTests
{
    [Fact]
    public void Instance_Is_Not_Null()
    {
        Assert.NotNull(CodecRegistry.Instance);
    }
}
