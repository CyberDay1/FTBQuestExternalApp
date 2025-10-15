# FTB Quest External App

This repository hosts the Windows front-end and shared libraries for an external editor targeting FTB Quests save data. The long-term goal is to provide a dedicated WinUI-based experience for exploring, editing, validating, and packaging quest content outside of the Minecraft client.

## Repository layout

- `apps/FTBQuestEditor.WinUI` – WinUI 3 desktop shell that will host the editor UI.
- `libs/` – Shared class libraries for codecs, schema definitions, validation, and runtime registries.
- `tests/FTBQuests.Tests` – xUnit test suite for shared libraries.
- `data/minecraft_registry/vanilla_items.json` – Seed data exported from the `probe` utility to ensure base Minecraft items are always present in the registry database. Regenerate by running the probe mod against a clean vanilla instance and copying the resulting `vanilla_items.json`.
