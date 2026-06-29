package com.example.bqgdaagent.service;

import com.example.bqgdaagent.client.VertexAiClient;
import com.example.bqgdaagent.model.CreateSessionRequest;
import com.example.bqgdaagent.model.QueryRequest;
import com.example.bqgdaagent.model.QueryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BigQueryAgentServiceTest {

    @Mock
    private VertexAiClient vertexAiClient;

    @Mock
    private VertexSessionService sessionService;

    @InjectMocks
    private BigQueryAgentService bigQueryAgentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(bigQueryAgentService, "reasoningEngineId", "test-engine-123");
    }

    @Test
    void query_withExistingSession_usesProvidedSessionId() {
        String sessionId = "existing-session-id";
        String userQuery = "What are the top 10 sales by region?";
        String expectedAnswer = "Here are the top 10 sales by region...";

        Map<String, Object> parts = new HashMap<>();
        parts.put("text", expectedAnswer);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(parts));

        Map<String, Object> output = new HashMap<>();
        output.put("content", content);

        Map<String, Object> rawResponse = new HashMap<>();
        rawResponse.put("output", output);

        when(vertexAiClient.queryReasoningEngine(eq("test-engine-123"), any())).thenReturn(rawResponse);

        QueryRequest request = new QueryRequest(userQuery, sessionId);
        QueryResponse response = bigQueryAgentService.query(request);

        assertThat(response.getSessionId()).isEqualTo(sessionId);
        assertThat(response.getAnswer()).isEqualTo(expectedAnswer);
        assertThat(response.getRawResponse()).isEqualTo(rawResponse);
    }

    @Test
    void query_withoutSession_createsNewSession() {
        String newSessionId = "newly-created-session";
        String userQuery = "Show me total revenue";
        String expectedAnswer = "Total revenue is...";

        com.example.bqgdaagent.model.SessionResponse mockSession =
                new com.example.bqgdaagent.model.SessionResponse();
        mockSession.setSessionId(newSessionId);
        mockSession.setName("projects/p/locations/us-central1/reasoningEngines/e/sessions/" + newSessionId);

        when(sessionService.createSession(any(CreateSessionRequest.class))).thenReturn(mockSession);

        Map<String, Object> rawResponse = new HashMap<>();
        rawResponse.put("output", new HashMap<String, Object>() {{ put("text", expectedAnswer); }});

        when(vertexAiClient.queryReasoningEngine(eq("test-engine-123"), any())).thenReturn(rawResponse);

        QueryRequest request = new QueryRequest(userQuery, null);
        QueryResponse response = bigQueryAgentService.query(request);

        assertThat(response.getSessionId()).isEqualTo(newSessionId);
        assertThat(response.getAnswer()).isEqualTo(expectedAnswer);
        verify(sessionService).createSession(any(CreateSessionRequest.class));
    }

    @Test
    void query_appendsConversationEventAfterResponse() {
        String sessionId = "test-session";
        String userQuery = "Count distinct customers";
        String expectedAnswer = "The count is 500";

        Map<String, Object> rawResponse = new HashMap<>();
        rawResponse.put("text", expectedAnswer);

        when(vertexAiClient.queryReasoningEngine(anyString(), any())).thenReturn(rawResponse);

        QueryRequest request = new QueryRequest(userQuery, sessionId);
        bigQueryAgentService.query(request);

        verify(sessionService).appendConversationEvent(sessionId, userQuery, expectedAnswer);
    }

    @Test
    void query_withNestedOutputStructure_extractsAnswerCorrectly() {
        String sessionId = "session-1";
        String expectedText = "Sales data from BigQuery";

        Map<String, Object> part = new HashMap<>();
        part.put("text", expectedText);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(part));

        Map<String, Object> output = new HashMap<>();
        output.put("content", content);

        Map<String, Object> rawResponse = new HashMap<>();
        rawResponse.put("output", output);

        when(vertexAiClient.queryReasoningEngine(anyString(), any())).thenReturn(rawResponse);

        QueryRequest request = new QueryRequest("Show sales", sessionId);
        QueryResponse response = bigQueryAgentService.query(request);

        assertThat(response.getAnswer()).isEqualTo(expectedText);
    }
}
