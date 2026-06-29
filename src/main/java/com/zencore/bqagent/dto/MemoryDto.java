package com.zencore.bqagent.dto;

import java.util.Map;

public record MemoryDto(
        String name,
        String fact,
        String createTime,
        String updateTime,
        Map<String, String> scope
) {
}
