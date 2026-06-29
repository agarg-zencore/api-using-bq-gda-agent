package com.zencore.bqagent.dto;

import java.util.List;

public record MessageListResponse(
        String conversationId,
        List<SessionMessageDto> messages
) {
}
