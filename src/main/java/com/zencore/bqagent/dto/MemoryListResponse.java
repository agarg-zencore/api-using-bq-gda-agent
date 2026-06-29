package com.zencore.bqagent.dto;

import java.util.List;

public record MemoryListResponse(
        String userId,
        List<MemoryDto> memories
) {
}
