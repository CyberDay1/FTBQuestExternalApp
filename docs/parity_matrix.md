# Quest Grid Parity Matrix

The editor grid mirrors the in-game quest layout. These constants are sourced from the latest parity audit of the Java edition client and are verified by automated tests to guarantee that documentation stays in sync with runtime behavior.

| Constant        | Value | Notes |
|-----------------|-------|-------|
| CellSize        | 32    | Size of a single grid cell in pixels. |
| IconScale       | 0.85  | Scaling factor applied to quest icons relative to their base texture. |
| Spacing         | 4     | Space in pixels between adjacent grid cells. |
| CellWithSpacing | 36    | Derived size of a quest slot including the inter-cell spacing (CellSize + Spacing). |

- `GridConstants.CellSize` – Base slot size (`apps/FTBQuestEditor.WinUI/ViewModels/GridConstants.cs`).
- `GridConstants.IconScale` – Icon render scale relative to the slot.
- `GridConstants.Spacing` – Gap between adjacent slots.
- `GridConstants.CellWithSpacing` – Convenience accessor for layout math.
- `GridConstants.Snap()` – Helper that normalizes coordinates to the grid.

When the in-game layout changes, adjust the constants above and record the new values here alongside the client build that intro
duced them.
