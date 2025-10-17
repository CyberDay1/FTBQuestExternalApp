namespace FTBQuests.Codecs.Model;

public interface ILayoutNode
{
    int PositionX { get; }

    int PositionY { get; }

    int Page { get; }

    Identifier IconId { get; }
}
