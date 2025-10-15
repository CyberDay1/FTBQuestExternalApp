# Format Reference

## ID Generation Rules

Quest and chapter identifiers follow the same incremental counter scheme used by FTB Quests:

- IDs are stored as 64-bit integers and are unique only within a single quest pack.
- Allocation always begins at `1` and increments until an unused value is found, skipping any registered IDs.
- Existing IDs from disk are registered during pack load, ensuring that `QuestPack.GetNextId()` continues the sequence seamlessly after reloads.
- Use `QuestPack.GetNextId()` whenever creating new chapters or quests (the helper methods `QuestPack.CreateChapter()` and `QuestPack.CreateQuest(...)` call it automatically).
- Dependencies still reference quest IDs numerically; a value of `0` indicates an unset or invalid reference and will be flagged by validation.

This behavior mirrors FTB Quests' native ID allocator, ensuring deterministic exports and round-trips.
