package com.zencore.bqagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.cloud.geminidataanalytics.v1beta.Message;
import com.google.cloud.geminidataanalytics.v1beta.SystemMessage;
import com.google.cloud.geminidataanalytics.v1beta.TextMessage;
import com.google.cloud.geminidataanalytics.v1beta.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SessionEventMapper {

    public List<Message> toGdaMessages(List<JsonNode> events) {
        List<Message> messages = new ArrayList<>();
        for (JsonNode event : events) {
            String text = extractText(event);
            if (text == null || text.isBlank()) {
                continue;
            }
            String author = event.path("author").asText("user");
            if ("user".equalsIgnoreCase(author)) {
                messages.add(Message.newBuilder()
                        .setUserMessage(UserMessage.newBuilder().setText(text).build())
                        .build());
            } else {
                messages.add(Message.newBuilder()
                        .setSystemMessage(SystemMessage.newBuilder()
                                .setText(TextMessage.newBuilder().addParts(text).build())
                                .build())
                        .build());
            }
        }
        return messages;
    }

    public List<JsonNode> toMessageMaps(List<JsonNode> events) {
        return new ArrayList<>(events);
    }

    public String extractText(JsonNode event) {
        JsonNode content = event.path("content");
        if (content.isMissingNode()) {
            content = event.path("rawEvent").path("content");
        }
        JsonNode parts = content.path("parts");
        if (!parts.isArray()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.has("text")) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(part.path("text").asText());
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
