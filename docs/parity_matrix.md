# Quest Grid Parity Matrix

The WinUI editor mirrors the spacing and icon sizing from the Minecraft quest screen. Update this stub as audits surface new val
ues that need to stay in lockstep with the game client.

## Constants tracked in code

- `GridConstants.CellSize` – Base slot size (`apps/FTBQuestEditor.WinUI/ViewModels/GridConstants.cs`).
- `GridConstants.IconScale` – Icon render scale relative to the slot.
- `GridConstants.Spacing` – Gap between adjacent slots.
- `GridConstants.CellWithSpacing` – Convenience accessor for layout math.
- `GridConstants.Snap()` – Helper that normalizes coordinates to the grid.

When the in-game layout changes, adjust the constants above and record the new values here alongside the client build that intro
duced them.
