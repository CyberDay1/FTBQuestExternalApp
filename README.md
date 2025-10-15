# FTB Quest External App

This repository hosts the Windows front-end and shared libraries for an external editor targeting FTB Quests save data. The long-term goal is to provide a dedicated WinUI-based experience for exploring, editing, validating, and packaging quest content outside of the Minecraft client.

## Repository layout

- `apps/FTBQuestEditor.WinUI` – WinUI 3 desktop shell that will host the editor UI.
- `libs/` – Shared class libraries for codecs, schema definitions, validation, and runtime registries.
- `tests/FTBQuests.Tests` – xUnit test suite for shared libraries.

## Documentation

- [Contributing guide](CONTRIBUTING.md) – Repository workflows, analyzer expectations, and commit etiquette.
- [Coding guidelines](docs/coding_guidelines.md) – Analyzer-aligned conventions for logging, exceptions, and serialization.
- [Quest data formats](docs/formats.md) – Key JSON fields consumed by codecs and schema generators.
- [Quest grid parity matrix](docs/parity_matrix.md) – Constants that keep the editor layout aligned with the game client.
