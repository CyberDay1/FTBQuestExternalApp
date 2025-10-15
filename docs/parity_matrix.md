# Quest Grid Parity Matrix

The editor grid mirrors the in-game quest layout. These constants are sourced from the latest parity audit of the Java edition client and are verified by automated tests to guarantee that documentation stays in sync with runtime behavior.

| Constant        | Value | Notes |
|-----------------|-------|-------|
| CellSize        | 32    | Size of a single grid cell in pixels. |
| IconScale       | 0.85  | Scaling factor applied to quest icons relative to their base texture. |
| Spacing         | 4     | Space in pixels between adjacent grid cells. |
| CellWithSpacing | 36    | Derived size of a quest slot including the inter-cell spacing (CellSize + Spacing). |

The in-game renderer composes quest icons inside 32×32 slots with a slight inset, leaving a 4 px gap around each cell. Icons are drawn at 85% scale to match the default hover/click hitbox size used throughout the quest UI.
