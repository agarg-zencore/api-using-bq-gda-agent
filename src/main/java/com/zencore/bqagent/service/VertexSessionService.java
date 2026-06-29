package com.zencore.bqagent.service;

import com.zencore.bqagent.client.VertexAiPlatformClient;
import com.zencore.bqagent.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class VertexSessionService {

    private static final Logger log = LoggerFactory.getLogger(VertexSessionService.class);

    private final AppProperties properties;
    private final VertexAiPlatformClient client;
    private final ObjectMapper objectMapper;

    public VertexSessionService(
            AppProperties properties,
            VertexAiPlatformClient client,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public String createSession(String userId, String sessionId) throws IOException, InterruptedException {
        requireEngineId();

        String resolvedUserId = resolveUserId(userId);
        String resolvedSessionId = sessionId != null && !sessionId.isBlank()
                ? sessionId
                : UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("userId", resolvedUserId);

        String path = "/" + properties.sessionsEngineParent() + "/sessions"
                + "?sessionId=" + resolvedSessionId;

        JsonNode response = client.post(path, body);
        client.pollOperation(response);

        log.info("Created Vertex session userId={} sessionId={}", resolvedUserId, resolvedSessionId);
        return resolvedSessionId;
    }

    public String getUserId(String sessionId) throws IOException, InterruptedException {
        JsonNode session = getSession(sessionId);
        return session.path("userId").asText(properties.resolvedDefaultUserId());
    }

    public JsonNode getSession(String sessionId) throws IOException, InterruptedException {
        requireEngineId();
        return client.get("/" + sessionResourceName(sessionId));
    }

    public void deleteSession(String sessionId) throws IOException, InterruptedException {
        requireEngineId();
        client.delete("/" + sessionResourceName(sessionId));
    }

    public List<JsonNode> listEvents(String sessionId) throws IOException, InterruptedException {
        requireEngineId();
        JsonNode response = client.get("/" + sessionResourceName(sessionId) + "/events");
        List<JsonNode> events = new ArrayList<>();
        JsonNode sessionEvents = response.path("sessionEvents");
        if (sessionEvents.isArray()) {
            sessionEvents.forEach(events::add);
        }
        return events;
    }

    public void appendUserMessage(String sessionId, String userId, String text, String invocationId)
            throws IOException, InterruptedException {
        appendEvent(sessionId, userId, "user", text, invocationId);
    }

    public void appendAgentMessage(String sessionId, String text, String invocationId)
            throws IOException, InterruptedException {
        appendEvent(sessionId, properties.resolvedDefaultUserId(), "agent", text, invocationId);
    }

    public String sessionResourceName(String sessionId) {
        return properties.sessionsEngineParent() + "/sessions/" + sessionId;
    }

    private void appendEvent(
            String sessionId,
            String userId,
            String author,
            String text,
            String invocationId)
            throws IOException, InterruptedException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("author", author);
        body.put("invocationId", invocationId);
        body.put("timestamp", Instant.now().toString());

        ObjectNode content = objectMapper.createObjectNode();
        content.put("role", "user".equals(author) ? "user" : "model");
        content.putArray("parts").addObject().put("text", text);
        body.set("content", content);

        client.postAction("/" + sessionResourceName(sessionId), "appendEvent", body);
    }

    private String resolveUserId(String userId) {
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        return properties.resolvedDefaultUserId();
    }

    private void requireEngineId() {
        if (properties.sessionsEngineId() == null || properties.sessionsEngineId().isBlank()) {
            throw new IllegalStateException(
                    "app.sessions-engine-id is blank. If you exported VERTEX_SESSIONS_ENGINE_ID "
                            + "as empty, run: unset VERTEX_SESSIONS_ENGINE_ID VERTEX_MEMORY_BANK_ENGINE_ID "
                            + "and restart the app.");
        }
    }
}
