package com.zencore.bqagent.service;

import com.zencore.bqagent.dto.ChatMessagePart;
import com.zencore.bqagent.dto.ChatMessageResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.geminidataanalytics.v1beta.Message;
import com.google.protobuf.util.JsonFormat;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MessageParser {

    private static final JsonFormat.Printer JSON_PRINTER =
            JsonFormat.printer().preservingProtoFieldNames();

    private final ObjectMapper objectMapper;

    public MessageParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ChatMessageResponse parse(Message message) {
        Map<String, Object> raw = protoToMap(message);
        List<ChatMessagePart> parts = new ArrayList<>();

        Map<String, Object> agentMessage = firstMap(raw, "agentMessage", "agent_message");
        if (agentMessage != null) {
            extractTextParts(agentMessage, parts);
            extractDataParts(agentMessage, parts);
        }

        Map<String, Object> systemMessage = firstMap(raw, "systemMessage", "system_message");
        if (systemMessage != null) {
            extractTextParts(systemMessage, parts);
            extractDataParts(systemMessage, parts);
        }

        Map<String, Object> userMessage = firstMap(raw, "userMessage", "user_message");
        if (userMessage != null && parts.isEmpty()) {
            parts.add(ChatMessagePart.user(stringOrNull(userMessage, "text")));
        }

        return new ChatMessageResponse(parts, raw);
    }

    public String extractFinalResponseText(List<ChatMessageResponse> responses) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessageResponse response : responses) {
            for (ChatMessagePart part : response.parts()) {
                if ("FINAL_RESPONSE".equals(part.textType()) && part.text() != null) {
                    if (!sb.isEmpty()) {
                        sb.append('\n');
                    }
                    sb.append(part.text());
                }
            }
        }
        if (!sb.isEmpty()) {
            return sb.toString();
        }
        for (ChatMessageResponse response : responses) {
            for (ChatMessagePart part : response.parts()) {
                if (part.text() != null && !part.text().isBlank()
                        && !"PROGRESS".equals(part.textType())) {
                    if (!sb.isEmpty()) {
                        sb.append('\n');
                    }
                    sb.append(part.text());
                }
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private void extractTextParts(Map<String, Object> container, List<ChatMessagePart> parts) {
        Object textMessageObj = container.get("textMessage");
        if (textMessageObj == null) {
            textMessageObj = container.get("text");
        }
        if (!(textMessageObj instanceof Map<?, ?> textMessage)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> textMessageMap = (Map<String, Object>) textMessage;
        Object partsObj = textMessageMap.get("parts");
        if (!(partsObj instanceof List<?> textParts)) {
            return;
        }
        String textType = stringOrNull(textMessageMap, "textType", "text_type");
        for (Object partObj : textParts) {
            if (partObj instanceof String text) {
                parts.add(ChatMessagePart.text(textType, text));
            } else if (partObj instanceof Map<?, ?> block) {
                @SuppressWarnings("unchecked")
                Map<String, Object> blockMap = (Map<String, Object>) block;
                parts.add(ChatMessagePart.text(
                        stringOrNull(blockMap, "textType", "text_type"),
                        stringOrNull(blockMap, "text")));
            }
        }
    }

    private void extractDataParts(Map<String, Object> container, List<ChatMessagePart> parts) {
        Object dataMessageObj = container.get("dataMessage");
        if (dataMessageObj == null) {
            dataMessageObj = container.get("data");
        }
        if (!(dataMessageObj instanceof Map<?, ?> dataMessage)) {
            return;
        }
        Object partsObj = dataMessage.get("parts");
        if (partsObj instanceof List<?> dataParts) {
            for (Object table : dataParts) {
                parts.add(ChatMessagePart.data(String.valueOf(table)));
            }
            return;
        }
        Object resultObj = dataMessage.get("result");
        if (resultObj != null) {
            parts.add(ChatMessagePart.data(String.valueOf(resultObj)));
        }
    }

    private Map<String, Object> protoToMap(Message message) {
        try {
            return objectMapper.readValue(JSON_PRINTER.print(message), new TypeReference<>() {
            });
        } catch (IOException ex) {
            return Map.of();
        }
    }

    private static Map<String, Object> firstMap(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            Object value = raw.get(key);
            if (value instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                return typed;
            }
        }
        return null;
    }

    private static String stringOrNull(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }
}
