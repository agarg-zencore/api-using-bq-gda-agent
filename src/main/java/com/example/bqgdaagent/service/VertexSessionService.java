package com.example.bqgdaagent.service;

import com.example.bqgdaagent.client.VertexAiClient;
import com.example.bqgdaagent.model.CreateSessionRequest;
import com.example.bqgdaagent.model.SessionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing conversation sessions via the Vertex AI Session and Memory Engine.
 * Sessions maintain conversation history across multiple turns of a conversation.
 */
@Service
public class VertexSessionService {

    private static final Logger logger = LoggerFactory.getLogger(VertexSessionService.class);

    private final VertexAiClient vertexAiClient;

    @Value("${vertex.ai.reasoning-engine-id}")
    private String reasoningEngineId;

    public VertexSessionService(VertexAiClient vertexAiClient) {
        this.vertexAiClient = vertexAiClient;
    }

    /**
     * Creates a new session in the Vertex AI Session and Memory Engine.
     *
     * @param request the session creation request containing optional userId and displayName
     * @return the created session details
     */
    public SessionResponse createSession(CreateSessionRequest request) {
        logger.info("Creating new session for user: {}", request.getUserId());

        Map<String, Object> body = new HashMap<>();
        if (request.getUserId() != null) {
            body.put("userId", request.getUserId());
        }
        if (request.getDisplayName() != null) {
            body.put("displayName", request.getDisplayName());
        }

        Map<String, Object> response = vertexAiClient.createSession(reasoningEngineId, body);
        return mapToSessionResponse(response);
    }

    /**
     * Retrieves an existing session by its ID.
     *
     * @param sessionId the session ID
     * @return the session details
     */
    public SessionResponse getSession(String sessionId) {
        logger.info("Retrieving session: {}", sessionId);
        Map<String, Object> response = vertexAiClient.getSession(reasoningEngineId, sessionId);
        return mapToSessionResponse(response);
    }

    /**
     * Lists all sessions for the configured Reasoning Engine.
     *
     * @return list of session details
     */
    @SuppressWarnings("unchecked")
    public List<SessionResponse> listSessions() {
        logger.info("Listing all sessions");
        Map<String, Object> response = vertexAiClient.listSessions(reasoningEngineId);
        List<?> sessions = (List<?>) response.getOrDefault("sessions", List.of());
        return sessions.stream()
                .filter(s -> s instanceof Map)
                .map(s -> mapToSessionResponse((Map<String, Object>) s))
                .toList();
    }

    /**
     * Deletes a session by its ID.
     *
     * @param sessionId the session ID to delete
     */
    public void deleteSession(String sessionId) {
        logger.info("Deleting session: {}", sessionId);
        vertexAiClient.deleteSession(reasoningEngineId, sessionId);
    }

    /**
     * Appends a user query and agent response event to the session memory.
     *
     * @param sessionId   the session ID
     * @param userInput   the user's query
     * @param agentOutput the agent's response
     */
    public void appendConversationEvent(String sessionId, String userInput, String agentOutput) {
        logger.debug("Appending conversation event to session: {}", sessionId);

        Map<String, Object> userEvent = new HashMap<>();
        userEvent.put("author", "user");
        userEvent.put("content", Map.of(
                "parts", List.of(Map.of("text", userInput))
        ));
        vertexAiClient.appendSessionEvent(reasoningEngineId, sessionId, userEvent);

        Map<String, Object> agentEvent = new HashMap<>();
        agentEvent.put("author", "agent");
        agentEvent.put("content", Map.of(
                "parts", List.of(Map.of("text", agentOutput))
        ));
        vertexAiClient.appendSessionEvent(reasoningEngineId, sessionId, agentEvent);
    }

    private SessionResponse mapToSessionResponse(Map<String, Object> map) {
        SessionResponse response = new SessionResponse();
        response.setName(String.valueOf(map.getOrDefault("name", "")));
        // Extract session ID from the full resource name (last segment)
        String name = response.getName();
        if (name.contains("/")) {
            response.setSessionId(name.substring(name.lastIndexOf('/') + 1));
        }
        response.setUserId(String.valueOf(map.getOrDefault("userId", "")));
        response.setDisplayName(String.valueOf(map.getOrDefault("displayName", "")));
        response.setCreateTime(String.valueOf(map.getOrDefault("createTime", "")));
        response.setUpdateTime(String.valueOf(map.getOrDefault("updateTime", "")));
        return response;
    }
}
