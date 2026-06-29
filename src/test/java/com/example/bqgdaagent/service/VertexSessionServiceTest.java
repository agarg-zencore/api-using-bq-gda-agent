package com.example.bqgdaagent.service;

import com.example.bqgdaagent.client.VertexAiClient;
import com.example.bqgdaagent.model.CreateSessionRequest;
import com.example.bqgdaagent.model.SessionResponse;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VertexSessionServiceTest {

    @Mock
    private VertexAiClient vertexAiClient;

    @InjectMocks
    private VertexSessionService vertexSessionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(vertexSessionService, "reasoningEngineId", "engine-456");
    }

    @Test
    void createSession_mapsResponseCorrectly() {
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("name", "projects/my-project/locations/us-central1/reasoningEngines/engine-456/sessions/sess-001");
        apiResponse.put("userId", "user-123");
        apiResponse.put("displayName", "Test Session");
        apiResponse.put("createTime", "2024-01-01T00:00:00Z");
        apiResponse.put("updateTime", "2024-01-01T00:00:00Z");

        when(vertexAiClient.createSession(eq("engine-456"), any())).thenReturn(apiResponse);

        CreateSessionRequest request = new CreateSessionRequest("user-123", "Test Session");
        SessionResponse response = vertexSessionService.createSession(request);

        assertThat(response.getSessionId()).isEqualTo("sess-001");
        assertThat(response.getUserId()).isEqualTo("user-123");
        assertThat(response.getDisplayName()).isEqualTo("Test Session");
    }

    @Test
    void getSession_returnsSessionDetails() {
        String sessionId = "sess-002";
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("name", "projects/p/locations/us-central1/reasoningEngines/engine-456/sessions/" + sessionId);
        apiResponse.put("userId", "user-456");
        apiResponse.put("createTime", "2024-02-01T00:00:00Z");
        apiResponse.put("updateTime", "2024-02-01T01:00:00Z");

        when(vertexAiClient.getSession("engine-456", sessionId)).thenReturn(apiResponse);

        SessionResponse response = vertexSessionService.getSession(sessionId);

        assertThat(response.getSessionId()).isEqualTo(sessionId);
        assertThat(response.getUserId()).isEqualTo("user-456");
    }

    @Test
    void listSessions_returnsMappedList() {
        Map<String, Object> s1 = new HashMap<>();
        s1.put("name", "projects/p/locations/us-central1/reasoningEngines/e/sessions/s1");
        s1.put("userId", "user-1");

        Map<String, Object> s2 = new HashMap<>();
        s2.put("name", "projects/p/locations/us-central1/reasoningEngines/e/sessions/s2");
        s2.put("userId", "user-2");

        Map<String, Object> listResponse = new HashMap<>();
        listResponse.put("sessions", List.of(s1, s2));

        when(vertexAiClient.listSessions("engine-456")).thenReturn(listResponse);

        List<SessionResponse> result = vertexSessionService.listSessions();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSessionId()).isEqualTo("s1");
        assertThat(result.get(1).getSessionId()).isEqualTo("s2");
    }

    @Test
    void listSessions_withEmptyResponse_returnsEmptyList() {
        when(vertexAiClient.listSessions("engine-456")).thenReturn(new HashMap<>());

        List<SessionResponse> result = vertexSessionService.listSessions();

        assertThat(result).isEmpty();
    }

    @Test
    void deleteSession_callsClientWithCorrectArgs() {
        String sessionId = "sess-to-delete";

        vertexSessionService.deleteSession(sessionId);

        verify(vertexAiClient).deleteSession("engine-456", sessionId);
    }

    @Test
    void appendConversationEvent_appendsBothUserAndAgentEvents() {
        String sessionId = "sess-conv";
        String userInput = "How many orders?";
        String agentOutput = "There are 1000 orders.";

        when(vertexAiClient.appendSessionEvent(any(), any(), any())).thenReturn(new HashMap<>());

        vertexSessionService.appendConversationEvent(sessionId, userInput, agentOutput);

        verify(vertexAiClient).appendSessionEvent(eq("engine-456"), eq(sessionId),
                argThatContains("author", "user"));
        verify(vertexAiClient).appendSessionEvent(eq("engine-456"), eq(sessionId),
                argThatContains("author", "agent"));
    }

    private Map<String, Object> argThatContains(String key, String value) {
        return org.mockito.ArgumentMatchers.argThat(map ->
                map != null && value.equals(map.get(key)));
    }
}
