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
- `data/minecraft_registry/vanilla_items.json` – Seed data exported from the `probe` utility to ensure base Minecraft items are always present in the registry database. Regenerate by running the probe mod against a clean vanilla instance and copying the resulting `vanilla_items.json`.

## Running Without Install

The WinUI 3 desktop application can be published as a portable, self-contained .NET 8 build that runs on any Windows 10 or later system without installing the .NET runtime.

1. Publish the portable build:
   ```powershell
   dotnet publish apps/FTBQuestEditor.WinUI -c Release -r win-x64 --self-contained true /p:PublishSingleFile=true /p:IncludeAllContentForSelfExtract=true
   ```
   The publish output is written to `dist/portable/` and contains a single executable bundled with the required dependencies, including SharpZipLib, Newtonsoft.Json, and CommunityToolkit.Mvvm.
2. Package the build for distribution:
   ```powershell
   pwsh tools/PackagePortable.ps1
   ```
   The script zips the portable publish output into `dist/FTBQuestEditor_Portable.zip` and validates that the archive remains under 250 MB.

All configuration, user data, and logs are created next to the executable inside `portable_data/` and `portable_logs/`, making the application fully portable.
