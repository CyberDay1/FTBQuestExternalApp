# FTB Quest External App — Java Scaffold

This repository contains the Gradle multi-module scaffold for the Java-based port of the FTB Quest External App.

## Module Overview

- `core-domain`: Domain models and business logic abstractions.
- `io-formats`: Serialization and file format codecs.
- `ingestion`: Data ingestion pipelines and adapters.
- `datastore`: Persistence and storage integrations.
- `services`: Application services and orchestration layer.
- `ui-javafx`: JavaFX desktop client entry point.
- `validation-tests`: End-to-end validation test suite.

## Getting Started

1. Ensure you have Java 21 and Gradle installed (Gradle wrapper to be added in future tasks).
2. Run `./gradlew build` after the wrapper is introduced to verify the scaffold builds successfully.
3. Add implementation code within the respective modules as development progresses.

## Conventions

- Commit only source files, configuration, and other text-based assets.
- Keep the project headless in CI environments.
- Use feature branches when developing new functionality.
