package com.zencore.bqagent.service;

import com.zencore.bqagent.client.VertexAiPlatformClient;
import com.zencore.bqagent.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.zencore.bqagent.dto.MemoryDto;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class VertexMemoryBankService {

    private static final Logger log = LoggerFactory.getLogger(VertexMemoryBankService.class);

    private final AppProperties properties;
    private final VertexAiPlatformClient client;
    private final ObjectMapper objectMapper;

    public VertexMemoryBankService(
            AppProperties properties,
            VertexAiPlatformClient client,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public List<String> retrieveMemories(String userId, String query)
            throws IOException, InterruptedException {
        if (properties.memoryBankEngineParent().contains("/reasoningEngines/")) {
            String engineId = properties.memoryBankEngineId();
            if ((engineId == null || engineId.isBlank())
                    && (properties.sessionsEngineId() == null || properties.sessionsEngineId().isBlank())) {
                return List.of();
            }
        }

        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode scope = objectMapper.createObjectNode();
        scope.put("user_id", resolveUserId(userId));
        body.set("scope", scope);

        if (query != null && !query.isBlank()) {
            ObjectNode similarity = objectMapper.createObjectNode();
            similarity.put("searchQuery", query);
            similarity.put("topK", 5);
            body.set("similaritySearchParams", similarity);
        } else {
            ObjectNode simple = objectMapper.createObjectNode();
            simple.put("topK", 5);
            body.set("simpleRetrievalParams", simple);
        }

        JsonNode response = client.post(
                "/" + properties.memoryBankEngineParent() + "/memories:retrieve", body);

        List<String> facts = new ArrayList<>();
        if (response.path("retrievedMemories").isArray()) {
            for (JsonNode item : response.path("retrievedMemories")) {
                String fact = item.path("memory").path("fact").asText(null);
                if (fact != null && !fact.isBlank()) {
                    facts.add(fact);
                }
            }
        }
        log.debug("Retrieved {} memories for userId={}", facts.size(), resolveUserId(userId));
        return facts;
    }

    public List<MemoryDto> listMemories(String userId) throws IOException, InterruptedException {
        if (!memoryBankConfigured()) {
            return List.of();
        }

        String resolvedUserId = resolveUserId(userId);
        String filter = "scope.user_id=\"" + resolvedUserId + "\"";

        List<MemoryDto> memories = new ArrayList<>();
        String pageToken = null;
        do {
            StringBuilder path = new StringBuilder()
                    .append("/")
                    .append(properties.memoryBankEngineParent())
                    .append("/memories?filter=")
                    .append(URLEncoder.encode(filter, StandardCharsets.UTF_8))
                    .append("&pageSize=100");
            if (pageToken != null && !pageToken.isBlank()) {
                path.append("&pageToken=")
                        .append(URLEncoder.encode(pageToken, StandardCharsets.UTF_8));
            }

            JsonNode response = client.get(path.toString());
            if (response.path("memories").isArray()) {
                for (JsonNode memory : response.path("memories")) {
                    memories.add(toMemoryDto(memory));
                }
            }
            pageToken = response.path("nextPageToken").asText(null);
        } while (pageToken != null && !pageToken.isBlank());

        log.info("Listed {} memories for userId={}", memories.size(), resolvedUserId);
        return memories;
    }

    public void generateMemoriesFromEvents(List<JsonNode> sessionEvents, String userId)
            throws IOException, InterruptedException {
        ArrayNode events = objectMapper.createArrayNode();
        for (JsonNode sessionEvent : sessionEvents) {
            JsonNode content = sessionEvent.path("content");
            if (content.isMissingNode()) {
                content = sessionEvent.path("rawEvent").path("content");
            }
            if (content.isMissingNode() || !content.path("parts").isArray()) {
                continue;
            }
            ObjectNode event = objectMapper.createObjectNode();
            event.set("content", content);
            events.add(event);
        }
        if (events.isEmpty()) {
            log.debug("Skipping memory generation — no session events with content");
            return;
        }

        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode directSource = objectMapper.createObjectNode();
        directSource.set("events", events);
        body.set("directContentsSource", directSource);

        ObjectNode scope = objectMapper.createObjectNode();
        scope.put("user_id", resolveUserId(userId));
        body.set("scope", scope);

        JsonNode response = client.post(
                "/" + properties.memoryBankEngineParent() + "/memories:generate", body);
        client.pollOperation(response);
        log.info("Generated memories from {} session events for userId={}", events.size(), resolveUserId(userId));
    }

    public String formatMemoriesContext(List<String> memories) {
        if (memories.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Relevant user context from prior conversations:\n");
        for (String memory : memories) {
            sb.append("- ").append(memory).append('\n');
        }
        return sb.toString();
    }

    private String resolveUserId(String userId) {
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        return properties.resolvedDefaultUserId();
    }

    private boolean memoryBankConfigured() {
        if (!properties.memoryBankEngineParent().contains("/reasoningEngines/")) {
            return false;
        }
        String engineId = properties.memoryBankEngineId();
        return (engineId != null && !engineId.isBlank())
                || (properties.sessionsEngineId() != null && !properties.sessionsEngineId().isBlank());
    }

    private MemoryDto toMemoryDto(JsonNode memory) {
        Map<String, String> scope = new HashMap<>();
        JsonNode scopeNode = memory.path("scope");
        if (scopeNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = scopeNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                scope.put(field.getKey(), field.getValue().asText());
            }
        }
        return new MemoryDto(
                memory.path("name").asText(null),
                memory.path("fact").asText(null),
                memory.path("createTime").asText(null),
                memory.path("updateTime").asText(null),
                scope);
    }
}
