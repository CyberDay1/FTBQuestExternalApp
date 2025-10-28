package dev.ftbq.editor.services.config;

/**
 * Loads configuration required to communicate with the OpenAI API.
 */
public final class OpenAIConfig {

    private static final String API_KEY_ENV = "OPENAI_API_KEY";
    private static final String ENDPOINT_ENV = "OPENAI_API_ENDPOINT";
    private static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    private OpenAIConfig() {
        throw new AssertionError("No instances");
    }

    /**
     * Returns the API key configured via the {@code OPENAI_API_KEY} environment variable.
     *
     * @return non-blank API key
     * @throws IllegalStateException if the environment variable is missing or blank
     */
    public static String getApiKey() {
        String apiKey = System.getenv(API_KEY_ENV);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Missing OpenAI API key. Set the OPENAI_API_KEY environment variable.");
        }
        return apiKey;
    }

    /**
     * Returns the chat completions endpoint. Defaults to {@code https://api.openai.com/v1/chat/completions} when the
     * {@code OPENAI_API_ENDPOINT} environment variable is not set.
     *
     * @return the configured endpoint URL
     */
    public static String getEndpoint() {
        String endpoint = System.getenv(ENDPOINT_ENV);
        if (endpoint == null || endpoint.isBlank()) {
            return DEFAULT_ENDPOINT;
        }
        return endpoint;
    }
}
