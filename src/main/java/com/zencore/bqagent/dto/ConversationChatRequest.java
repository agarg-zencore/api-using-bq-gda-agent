package com.zencore.bqagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConversationChatRequest(
        @NotBlank @Size(max = 8000) String message,
        String userId
) {
}
