package com.zencore.bqagent.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SubmitFeedbackRequest(
        @NotNull Boolean positive,
        @Size(max = 4096) String feedbackMessage,
        List<@Size(max = 256) String> categories,
        String invocationId,
        String userId
) {
}
