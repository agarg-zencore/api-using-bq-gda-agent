package com.zencore.bqagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String gcpProjectId,
        String gcpLocation,
        String dataAgentId,
        String vertexLocation,
        String sessionsEngineId,
        String memoryBankEngineId,
        String defaultUserId,
        int chatTimeoutSeconds,
        String feedbackDataset,
        String feedbackTable,
        boolean feedbackAutoCreateTable
) {
    public String parent() {
        return "projects/" + gcpProjectId + "/locations/" + gcpLocation;
    }

    public String vertexApiBaseUrl() {
        return "https://" + vertexLocation + "-aiplatform.googleapis.com/v1beta1";
    }

    public String sessionsEngineParent() {
        return "projects/" + gcpProjectId + "/locations/" + vertexLocation
                + "/reasoningEngines/" + sessionsEngineId;
    }

    public String memoryBankEngineParent() {
        String engineId = memoryBankEngineId == null || memoryBankEngineId.isBlank()
                ? sessionsEngineId
                : memoryBankEngineId;
        return "projects/" + gcpProjectId + "/locations/" + vertexLocation
                + "/reasoningEngines/" + engineId;
    }

    public String resolvedDefaultUserId() {
        return defaultUserId == null || defaultUserId.isBlank() ? "default-user" : defaultUserId;
    }

    public boolean feedbackConfigured() {
        return feedbackDataset != null && !feedbackDataset.isBlank()
                && feedbackTable != null && !feedbackTable.isBlank();
    }
}
