# FTB Quests Developer CLI

The developer CLI provides lightweight tooling for schema management and pack validation while working on the project.

## Building

```bash
dotnet build tools/DevCli/DevCli.csproj
```

## Commands

### Schema Emit

Generate the JSON schema bundle for the current serialization version.

```bash
dotnet run --project tools/DevCli -- schema emit --out tools/Schemas/v1_21_1
```

If the `--out` option is omitted the default location of `tools/Schemas/v1_21_1` is used.

### Validate

Validate an extracted FTB Quests pack.

```bash
dotnet run --project tools/DevCli -- validate --pack path/to/unpacked/pack
```

The command prints any discovered issues and returns a non-zero exit code if validation fails.
