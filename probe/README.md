# FTB Quest Registry Probe

Minimal NeoForge 1.21.1 mod that emits runtime registry and language dumps when a dedicated server starts.

## Building

```bash
./gradlew build
```

The wrapper downloads Gradle 8.10.2 automatically if the bootstrap JAR is missing.

## Configuration

* `-Dprobe.out=/absolute/or/relative/path` – overrides the output directory. Defaults to `probe_output` under the working directory.
* `-Dprobe.includeNbt=true` – include the default item stack NBT payload in the registry dump. Disabled by default.

## Output

Running a server with the mod writes two JSON files inside the output directory:

* `registry_dump.json` – registry entries (items, blocks, fluids) and tag lists.
* `lang_index.json` – aggregation of translations from the loaded language assets, keyed by language code.
