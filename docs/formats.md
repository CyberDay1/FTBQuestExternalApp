# Quest Data Formats

This reference summarizes the primary JSON fields handled by the codecs and schema emitter. Use it when preparing fixtures, vali
dating imports, or coordinating with in-game data packs.

## Chapters

The chapter payload modeled in [`Chapter`](../libs/FTBQuests.Codecs/Model/Chapter.cs) and emitted by [`SchemaEmitter.BuildChap
terSchema`](../libs/FTBQuests.Schema/SchemaEmitter.cs) must include:

- `id` (UUID) – Stable identifier used to join quests to a chapter. The codec maps this to `Chapter.Id`.
- `title` (string) – Display name surfaced in navigation. Populates `Chapter.Title`.
- `description` (nullable string) – Long-form text shown in the sidebar. Written to `Chapter.Description`.
- `icon` / `iconId` / `icon_id` (namespaced identifier) – Optional texture reference normalized into `Chapter.IconId`.
- `quests` (array) – Ordered list of quest documents, maintained as `Chapter.Quests`.

Additional vendor-specific fields are captured in the `extra` property bag (`Chapter.Extra`).

## Quests

Quest entries parsed by [`QuestConverter`](../libs/FTBQuests.Codecs/Serialization/QuestConverter.cs) and described in [`BuildQue
stSchema`](../libs/FTBQuests.Schema/SchemaEmitter.cs) include:

- `id` (UUID) – Unique quest identifier stored in `Quest.Id`.
- `title` / `subtitle` (strings) – Display text surfaced in tooltips (`Quest.Title`, `Quest.Subtitle`).
- `icon`, `iconId`, `icon_id` (namespaced identifier) – Texture handle normalized into `Quest.IconId`.
- `page` (integer) – Optional page index for multi-page chapters (`Quest.Page`).
- `x`, `y` (integers) – Grid coordinates measured in pixels and persisted in `Quest.PositionX` and `Quest.PositionY`.
- `dependencies` (array of UUIDs) – Unlock requirements maintained in `Quest.Dependencies`.
- `tasks` / `rewards` (arrays) – Nested collections converted to `Quest.Tasks` and `Quest.Rewards`.

Unknown quest fields are routed to the extensible `extra` bag (`Quest.Extra`).

## Tasks

Task payloads are discriminated by the `type` field and materialized into subclasses of [`TaskBase`](../libs/FTBQuests.Codecs/Mo
del/Tasks/TaskBase.cs). Common properties include:

- `type` (string) – Lowercase identifier resolved against the registry (`TaskBase.TypeId`).
- Objective-specific fields (for example `item`, `advancement`, `entity`, `target`) captured by [`KnownTasks`](../libs/FTBQuests
.Codecs/Model/Tasks/KnownTasks.cs) when present.
- Arbitrary data, which flows into `TaskBase.Extra` so custom task definitions remain round-trippable.

## Rewards

Rewards follow the same pattern as tasks, with the discriminator and known fields described in [`KnownRewards`](../libs/FTBQuest
s.Codecs/Model/Rewards/KnownRewards.cs):

- `type` (string) – Lowercase reward identifier mapped to `IReward.TypeId`.
- Inventory-specific properties (for example `item`, `loot_table`) stored on the concrete reward model.
- Additional properties forwarded to the reward's `Extra` bag for addon compatibility.

## Identifiers and validation

Namespaced resource identifiers are validated by [`Identifier`](../libs/FTBQuests.Codecs/Model/Identifier.cs), which enforces the
`namespace:path` format used across tasks, rewards, and icons. Schema files generated through [`SchemaEmitter`](../libs/FTBQuest
s.Schema/SchemaEmitter.cs) expose these constraints for external tooling.
# Format Reference

## ID Generation Rules

Quest and chapter identifiers follow the same incremental counter scheme used by FTB Quests:

- IDs are stored as 64-bit integers and are unique only within a single quest pack.
- Allocation always begins at `1` and increments until an unused value is found, skipping any registered IDs.
- Existing IDs from disk are registered during pack load, ensuring that `QuestPack.GetNextId()` continues the sequence seamlessly after reloads.
- Use `QuestPack.GetNextId()` whenever creating new chapters or quests (the helper methods `QuestPack.CreateChapter()` and `QuestPack.CreateQuest(...)` call it automatically).
- Dependencies still reference quest IDs numerically; a value of `0` indicates an unset or invalid reference and will be flagged by validation.

This behavior mirrors FTB Quests' native ID allocator, ensuring deterministic exports and round-trips.
