package com.zencore.bqagent.dto;

public record ConversationResponse(
        String conversationId,
        String userId,
        String sessionResourceName
) {
}
