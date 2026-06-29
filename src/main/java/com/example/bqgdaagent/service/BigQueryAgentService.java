package com.example.bqgdaagent.service;

import com.example.bqgdaagent.client.VertexAiClient;
import com.example.bqgdaagent.model.QueryRequest;
import com.example.bqgdaagent.model.QueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for querying the BigQuery Gemini Data Agent (BQ GDA) via the
 * Vertex AI Reasoning Engine Conversation Analytic API.
 *
 * <p>The BQ GDA is a deployed Reasoning Engine on Vertex AI that uses Gemini
 * to answer natural language questions about BigQuery datasets. Session context
 * is passed through to maintain multi-turn conversation history via the
 * Vertex Session and Memory Engine.</p>
 */
@Service
public class BigQueryAgentService {

    private static final Logger logger = LoggerFactory.getLogger(BigQueryAgentService.class);

    private final VertexAiClient vertexAiClient;
    private final VertexSessionService sessionService;

    @Value("${vertex.ai.reasoning-engine-id}")
    private String reasoningEngineId;

    public BigQueryAgentService(VertexAiClient vertexAiClient, VertexSessionService sessionService) {
        this.vertexAiClient = vertexAiClient;
        this.sessionService = sessionService;
    }

    /**
     * Sends a natural language query to the BigQuery Gemini Data Agent.
     * If a session ID is provided, the conversation context from the
     * Vertex Session and Memory Engine is included to support multi-turn
     * conversations. If no session ID is provided, a new session is created.
     *
     * @param request the query request containing the user's question and optional session ID
     * @return the query response containing the agent's answer and session ID
     */
    public QueryResponse query(QueryRequest request) {
        String sessionId = request.getSessionId();

        // Create a session if none provided
        if (sessionId == null || sessionId.isBlank()) {
            logger.info("No session provided, creating new session");
            sessionId = sessionService.createSession(new com.example.bqgdaagent.model.CreateSessionRequest())
                    .getSessionId();
        }

        logger.info("Querying BQ GDA with session: {}", sessionId);

        Map<String, Object> input = buildQueryInput(request.getQuery(), sessionId);
        Map<String, Object> rawResponse = vertexAiClient.queryReasoningEngine(reasoningEngineId, input);

        String answer = extractAnswer(rawResponse);

        // Persist conversation turn in the Session and Memory Engine
        sessionService.appendConversationEvent(sessionId, request.getQuery(), answer);

        return new QueryResponse(sessionId, answer, rawResponse);
    }

    /**
     * Builds the request body for the Reasoning Engine query API.
     * The input wraps the user message and session information as expected
     * by the Conversation Analytic API contract.
     */
    private Map<String, Object> buildQueryInput(String query, String sessionId) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("parts", List.of(Map.of("text", query)));

        Map<String, Object> input = new HashMap<>();
        input.put("input", Map.of(
                "messages", List.of(message),
                "session_id", sessionId
        ));
        return input;
    }

    /**
     * Extracts the text answer from the Reasoning Engine response.
     * The response structure follows the Conversation Analytic API format.
     */
    @SuppressWarnings("unchecked")
    private String extractAnswer(Map<String, Object> rawResponse) {
        try {
            // Try to extract from output.content.parts[0].text structure
            Object output = rawResponse.get("output");
            if (output instanceof Map<?, ?> outputMap) {
                Object content = outputMap.get("content");
                if (content instanceof Map<?, ?> contentMap) {
                    Object parts = contentMap.get("parts");
                    if (parts instanceof List<?> partsList && !partsList.isEmpty()) {
                        Object firstPart = partsList.get(0);
                        if (firstPart instanceof Map<?, ?> partMap) {
                            Object text = partMap.get("text");
                            if (text != null) {
                                return String.valueOf(text);
                            }
                        }
                    }
                }
                // Fallback: try output.text
                Object text = outputMap.get("text");
                if (text != null) {
                    return String.valueOf(text);
                }
            }
            // Fallback: try top-level text field
            Object text = rawResponse.get("text");
            if (text != null) {
                return String.valueOf(text);
            }
        } catch (Exception e) {
            logger.warn("Could not parse structured answer from response, returning raw", e);
        }
        return String.valueOf(rawResponse);
    }
}
