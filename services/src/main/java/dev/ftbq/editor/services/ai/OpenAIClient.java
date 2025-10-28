package dev.ftbq.editor.services.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.ftbq.editor.services.config.OpenAIConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

/**
 * Lightweight HTTP client responsible for calling the OpenAI chat completions API.
 */
public final class OpenAIClient {

    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String apiKey;

    public OpenAIClient() {
        this(HttpClient.newHttpClient(), new ObjectMapper());
    }

    public OpenAIClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.endpoint = OpenAIConfig.getEndpoint();
        this.apiKey = OpenAIConfig.getApiKey();
    }

    /**
     * Calls the OpenAI API with the supplied prompt and returns the textual response content.
     *
     * @param prompt the fully rendered user prompt
     * @return chat completion content
     * @throws OpenAIClientException when communication or parsing fails
     */
    public String generateQuestChapter(String prompt) {
        Objects.requireNonNull(prompt, "prompt");
        try {
            String requestBody = buildRequestBody(prompt);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OpenAIClientException("OpenAI API responded with status " + response.statusCode() + ": "
                        + response.body());
            }
            return extractMessageContent(response.body());
        } catch (IOException ex) {
            throw new OpenAIClientException("Failed to call OpenAI API", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OpenAIClientException("OpenAI API call interrupted", ex);
        }
    }

    private String buildRequestBody(String prompt) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", DEFAULT_MODEL);
        root.put("temperature", 0.7);
        ArrayNode messages = root.putArray("messages");

        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", "You produce FTB Quests chapters in SNBT format. "
                + "Respond with syntactically correct SNBT only.");

        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        return objectMapper.writeValueAsString(root);
    }

    private String extractMessageContent(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode message = choices.get(0).path("message");
            if (message.hasNonNull("content")) {
                return message.get("content").asText();
            }
        }
        throw new OpenAIClientException("OpenAI API response missing chat content: " + responseBody);
    }

    public static final class OpenAIClientException extends RuntimeException {
        public OpenAIClientException(String message) {
            super(message);
        }

        public OpenAIClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
