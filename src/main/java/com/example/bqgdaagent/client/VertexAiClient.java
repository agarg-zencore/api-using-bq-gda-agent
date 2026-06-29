package com.example.bqgdaagent.client;

import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Map;

/**
 * HTTP client for Vertex AI APIs, including the Reasoning Engine (BQ GDA)
 * and the Session and Memory Engine.
 */
@Component
public class VertexAiClient {

    private static final Logger logger = LoggerFactory.getLogger(VertexAiClient.class);

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final GoogleCredentials credentials;

    @Value("${gcp.project-id}")
    private String projectId;

    @Value("${gcp.location:us-central1}")
    private String location;

    @Value("${vertex.ai.api-version:v1beta1}")
    private String apiVersion;

    public VertexAiClient(WebClient vertexAiWebClient, GoogleCredentials googleCredentials) {
        this.webClient = vertexAiWebClient;
        this.credentials = googleCredentials;
    }

    /**
     * Builds the base resource path for a Reasoning Engine.
     */
    public String reasoningEnginePath(String engineId) {
        return String.format("/projects/%s/locations/%s/reasoningEngines/%s",
                projectId, location, engineId);
    }

    /**
     * Calls the Vertex AI Reasoning Engine (BQ GDA) with a user query and optional session.
     *
     * @param engineId the Reasoning Engine ID
     * @param input    the request body map containing the query and optional session ID
     * @return the raw response map from the engine
     */
    public Map<String, Object> queryReasoningEngine(String engineId, Map<String, Object> input) {
        String path = String.format("/%s%s:query", apiVersion, reasoningEnginePath(engineId));
        logger.debug("Querying Reasoning Engine at path: {}", path);

        return webClient.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(input)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .onErrorResume(WebClientResponseException.class, ex -> {
                    logger.error("Error querying Reasoning Engine: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                    return Mono.error(new RuntimeException("Reasoning Engine query failed: " + ex.getMessage(), ex));
                })
                .block();
    }

    /**
     * Creates a new session in the Vertex AI Session and Memory Engine.
     *
     * @param engineId the Reasoning Engine ID that owns the session namespace
     * @param body     the session creation request body
     * @return the created session map
     */
    public Map<String, Object> createSession(String engineId, Map<String, Object> body) {
        String path = String.format("/%s%s/sessions", apiVersion, reasoningEnginePath(engineId));
        logger.debug("Creating session at path: {}", path);

        return webClient.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .onErrorResume(WebClientResponseException.class, ex -> {
                    logger.error("Error creating session: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                    return Mono.error(new RuntimeException("Session creation failed: " + ex.getMessage(), ex));
                })
                .block();
    }

    /**
     * Retrieves an existing session from the Vertex AI Session and Memory Engine.
     *
     * @param engineId  the Reasoning Engine ID
     * @param sessionId the session ID
     * @return the session details map
     */
    public Map<String, Object> getSession(String engineId, String sessionId) {
        String path = String.format("/%s%s/sessions/%s", apiVersion, reasoningEnginePath(engineId), sessionId);
        logger.debug("Getting session at path: {}", path);

        return webClient.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .onErrorResume(WebClientResponseException.class, ex -> {
                    logger.error("Error getting session: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                    return Mono.error(new RuntimeException("Session retrieval failed: " + ex.getMessage(), ex));
                })
                .block();
    }

    /**
     * Lists all sessions for a given Reasoning Engine.
     *
     * @param engineId the Reasoning Engine ID
     * @return the list sessions response map
     */
    public Map<String, Object> listSessions(String engineId) {
        String path = String.format("/%s%s/sessions", apiVersion, reasoningEnginePath(engineId));
        logger.debug("Listing sessions at path: {}", path);

        return webClient.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .onErrorResume(WebClientResponseException.class, ex -> {
                    logger.error("Error listing sessions: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                    return Mono.error(new RuntimeException("Session listing failed: " + ex.getMessage(), ex));
                })
                .block();
    }

    /**
     * Deletes a session from the Vertex AI Session and Memory Engine.
     *
     * @param engineId  the Reasoning Engine ID
     * @param sessionId the session ID to delete
     */
    public void deleteSession(String engineId, String sessionId) {
        String path = String.format("/%s%s/sessions/%s", apiVersion, reasoningEnginePath(engineId), sessionId);
        logger.debug("Deleting session at path: {}", path);

        webClient.delete()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(WebClientResponseException.class, ex -> {
                    logger.error("Error deleting session: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                    return Mono.error(new RuntimeException("Session deletion failed: " + ex.getMessage(), ex));
                })
                .block();
    }

    /**
     * Appends a conversation event to a session in the Session and Memory Engine.
     *
     * @param engineId  the Reasoning Engine ID
     * @param sessionId the session ID
     * @param event     the event body to append
     * @return the append event response map
     */
    public Map<String, Object> appendSessionEvent(String engineId, String sessionId, Map<String, Object> event) {
        String path = String.format("/%s%s/sessions/%s:appendEvent",
                apiVersion, reasoningEnginePath(engineId), sessionId);
        logger.debug("Appending event to session at path: {}", path);

        return webClient.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(event)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .onErrorResume(WebClientResponseException.class, ex -> {
                    logger.error("Error appending session event: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                    return Mono.error(new RuntimeException("Session event append failed: " + ex.getMessage(), ex));
                })
                .block();
    }

    private String getAccessToken() {
        try {
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException e) {
            throw new RuntimeException("Failed to obtain GCP access token", e);
        }
    }
}
