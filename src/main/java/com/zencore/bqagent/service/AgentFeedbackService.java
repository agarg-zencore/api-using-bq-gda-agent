package com.zencore.bqagent.service;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.Dataset;
import com.google.cloud.bigquery.DatasetInfo;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.StandardTableDefinition;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableInfo;
import com.zencore.bqagent.config.AppProperties;
import com.zencore.bqagent.dto.FeedbackResponse;
import com.zencore.bqagent.dto.SubmitFeedbackRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(AgentFeedbackService.class);

    private final AppProperties properties;
    private final BigQuery bigQuery;
    private final VertexSessionService vertexSessionService;

    public AgentFeedbackService(
            AppProperties properties,
            BigQuery bigQuery,
            VertexSessionService vertexSessionService) {
        this.properties = properties;
        this.bigQuery = bigQuery;
        this.vertexSessionService = vertexSessionService;
    }

    public FeedbackResponse submitFeedback(
            String conversationId,
            SubmitFeedbackRequest request,
            String headerUserId) {
        requireFeedbackConfigured();

        String userId = resolveUserId(conversationId, request.userId(), headerUserId);
        String feedbackId = UUID.randomUUID().toString();
        String createdAt = Instant.now().toString();

        ensureTableExists();

        Map<String, Object> row = new HashMap<>();
        row.put("feedback_id", feedbackId);
        row.put("created_at", createdAt);
        row.put("user_id", userId);
        row.put("conversation_id", conversationId);
        row.put("invocation_id", blankToNull(request.invocationId()));
        row.put("data_agent_id", properties.dataAgentId());
        row.put("positive", request.positive());
        row.put("feedback_message", blankToNull(request.feedbackMessage()));
        row.put("categories", request.categories() != null ? request.categories() : List.of());

        TableId tableId = feedbackTableId();
        InsertAllResponse response = bigQuery.insertAll(
                InsertAllRequest.newBuilder(tableId).addRow(row).build());

        if (response.hasErrors()) {
            String detail = response.getInsertErrors().values().stream()
                    .flatMap(List::stream)
                    .map(error -> error.getMessage())
                    .findFirst()
                    .orElse("Unknown BigQuery insert error");
            throw new IllegalStateException("Failed to store feedback in BigQuery: " + detail);
        }

        log.info(
                "Stored agent feedback feedbackId={} conversationId={} userId={} positive={}",
                feedbackId,
                conversationId,
                userId,
                request.positive());

        return new FeedbackResponse("Feedback submitted successfully", feedbackId);
    }

    private void requireFeedbackConfigured() {
        if (!properties.feedbackConfigured()) {
            throw new IllegalStateException(
                    "Feedback storage is not configured. Set app.feedback-dataset and "
                            + "app.feedback-table (or FEEDBACK_DATASET / FEEDBACK_TABLE env vars).");
        }
    }

    private String resolveUserId(String conversationId, String bodyUserId, String headerUserId) {
        if (bodyUserId != null && !bodyUserId.isBlank()) {
            return bodyUserId;
        }
        if (headerUserId != null && !headerUserId.isBlank()) {
            return headerUserId;
        }
        try {
            return vertexSessionService.getUserId(conversationId);
        } catch (Exception ex) {
            return properties.resolvedDefaultUserId();
        }
    }

    private TableId feedbackTableId() {
        return TableId.of(properties.gcpProjectId(), properties.feedbackDataset(), properties.feedbackTable());
    }

    private void ensureTableExists() {
        if (!properties.feedbackAutoCreateTable()) {
            return;
        }

        TableId tableId = feedbackTableId();
        if (bigQuery.getTable(tableId) != null) {
            return;
        }

        ensureDatasetExists(tableId.getDataset());

        Schema schema = Schema.of(
                Field.of("feedback_id", StandardSQLTypeName.STRING),
                Field.of("created_at", StandardSQLTypeName.TIMESTAMP),
                Field.of("user_id", StandardSQLTypeName.STRING),
                Field.of("conversation_id", StandardSQLTypeName.STRING),
                Field.of("invocation_id", StandardSQLTypeName.STRING),
                Field.of("data_agent_id", StandardSQLTypeName.STRING),
                Field.of("positive", StandardSQLTypeName.BOOL),
                Field.of("feedback_message", StandardSQLTypeName.STRING),
                Field.newBuilder("categories", StandardSQLTypeName.STRING)
                        .setMode(Field.Mode.REPEATED)
                        .build());

        TableInfo tableInfo = TableInfo.newBuilder(
                        tableId, StandardTableDefinition.of(schema))
                .build();

        try {
            bigQuery.create(tableInfo);
            log.info("Created BigQuery feedback table: {}.{}.{}",
                    tableId.getProject(), tableId.getDataset(), tableId.getTable());
        } catch (BigQueryException ex) {
            if (ex.getCode() != 409) {
                throw ex;
            }
        }
    }

    private void ensureDatasetExists(String datasetId) {
        Dataset dataset = bigQuery.getDataset(datasetId);
        if (dataset != null) {
            return;
        }
        try {
            bigQuery.create(DatasetInfo.newBuilder(datasetId).build());
            log.info("Created BigQuery dataset for feedback: {}", datasetId);
        } catch (BigQueryException ex) {
            if (ex.getCode() != 409) {
                throw ex;
            }
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
