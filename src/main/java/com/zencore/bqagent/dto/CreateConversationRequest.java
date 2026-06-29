package com.zencore.bqagent.dto;

public record CreateConversationRequest(
        String conversationId,
        String userId
) {
}
