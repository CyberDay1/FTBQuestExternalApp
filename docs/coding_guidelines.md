# Coding Guidelines

This repository enforces nullable reference types, the latest C# language features, and analyzer warnings as errors. The following guidelines summarize the practices that the analyzers expect developers to follow.

## Logging

- Use structured logging with message templates rather than string concatenation.
- Always prefer logging abstractions that support dependency injection (for example, `ILogger<T>`).
- Include contextual information as named properties (e.g., `logger.LogInformation("Processing quest {QuestId}", questId);`).
- Log exceptions with `LogError` or `LogCritical` while passing the exception instance as the first argument.
- Avoid logging sensitive data such as authentication tokens or personal information.

## Exceptions

- Throw the most specific exception type available (e.g., `ArgumentException`, `InvalidOperationException`).
- Validate all public API arguments with `ArgumentNullException.ThrowIfNull` or `ArgumentException.ThrowIfNullOrEmpty` as appropriate.
- Never swallow exceptions; either handle them explicitly or rethrow using `throw;` to preserve the stack trace.
- Provide clear error messages that describe the precondition or invariant that failed.
- Avoid using exceptions for control flow—prefer explicit checks.

## Serialization

- Use explicit models with `[JsonPropertyName]` or equivalent attributes when serialized names differ from property names.
- Always opt into immutable DTOs where feasible by using `init` accessors or constructors to avoid partially constructed instances.
- Validate incoming payloads before deserialization when possible to mitigate security issues.
- Prefer `System.Text.Json` with configured converters over custom parsers unless performance or compatibility requires otherwise.
- When introducing new schema changes, ensure backwards compatibility by allowing optional fields and providing sensible defaults.

Adhering to these standards keeps the codebase analyzer-compliant and predictable across all projects.
