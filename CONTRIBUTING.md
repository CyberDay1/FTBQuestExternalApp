# Contributing to FTB Quest External App

Thank you for your interest in contributing! This document summarizes the day-to-day workflows and guardrails that keep the repository healthy.

## Repository expectations

- **Source-only commits.** Do not commit compiled binaries, images, archives, or other generated outputs. Restrict changes to source, JSON, and text assets so the history remains reviewable.
- **Headless-first development.** The WinUI client should not be launched as part of validation. Continuous integration runs in a headless environment, so all verification steps must complete without a desktop shell.

## Tooling and analyzers

- The solution targets the latest C# preview features with nullable reference types enabled and warnings treated as errors. These settings are enforced centrally in [`Directory.Build.props`](Directory.Build.props).
- StyleCop analyzers are configured through [`stylecop.json`](stylecop.json). Follow the documented conventions in [`docs/coding_guidelines.md`](docs/coding_guidelines.md) for logging, exception handling, and serialization.
- Roslyn analyzers and StyleCop are executed during every build; keep local builds clean before sending a pull request.

## Testing workflow

- Run `dotnet build` and `dotnet test` from the repository root. Tests live in [`tests/FTBQuests.Tests`](tests/FTBQuests.Tests) and cover codecs, registries, and schema validation helpers.
- Add or update tests alongside behavioral changes. Prefer deterministic, data-driven tests that do not rely on the game client or external services.

## Commit messages and pull requests

- Write imperative, present-tense commit messages that describe the change, e.g., `Add quest schema documentation`.
- Keep commits focused; if you are fixing unrelated issues, submit them as separate commits or pull requests.
- Reference relevant issues when applicable and provide a short summary of the change set in the pull request description.

Following these guidelines ensures contributions integrate smoothly with the automated checks and remain maintainable for future collaborators.
