package com.zencore.bqagent.dto;

import java.util.List;

public record SessionMessageDto(
        String author,
        String text,
        String invocationId,
        String timestamp
) {
}
