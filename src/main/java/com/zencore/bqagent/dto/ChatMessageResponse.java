package com.zencore.bqagent.dto;

import java.util.List;
import java.util.Map;

public record ChatMessageResponse(
        List<ChatMessagePart> parts,
        Map<String, Object> raw
) {
}
