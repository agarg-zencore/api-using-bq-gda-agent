package com.zencore.bqagent.dto;

public record ChatMessagePart(
        String textType,
        String text,
        String messageType
) {
    public static ChatMessagePart text(String textType, String text) {
        return new ChatMessagePart(textType, text, "text");
    }

    public static ChatMessagePart data(String json) {
        return new ChatMessagePart(null, json, "data");
    }

    public static ChatMessagePart user(String text) {
        return new ChatMessagePart(null, text, "user");
    }
}
