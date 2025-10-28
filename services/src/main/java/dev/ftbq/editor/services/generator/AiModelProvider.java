package dev.ftbq.editor.services.generator;

/**
 * Abstraction over an AI model capable of producing quest chapters from prompts.
 */
public interface AiModelProvider {
    ModelResponse generate(AiGenerationRequest request);
}
