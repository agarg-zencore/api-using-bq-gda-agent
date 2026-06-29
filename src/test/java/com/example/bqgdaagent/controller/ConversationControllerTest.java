package com.example.bqgdaagent.controller;

import com.example.bqgdaagent.model.CreateSessionRequest;
import com.example.bqgdaagent.model.QueryRequest;
import com.example.bqgdaagent.model.QueryResponse;
import com.example.bqgdaagent.model.SessionResponse;
import com.example.bqgdaagent.service.BigQueryAgentService;
import com.example.bqgdaagent.service.VertexSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConversationController.class)
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BigQueryAgentService bigQueryAgentService;

    @MockBean
    private VertexSessionService vertexSessionService;

    @Test
    void postQuery_returnsOkWithAnswer() throws Exception {
        QueryResponse queryResponse = new QueryResponse("sess-1", "Total is 500", null);
        when(bigQueryAgentService.query(any(QueryRequest.class))).thenReturn(queryResponse);

        QueryRequest request = new QueryRequest("What is the total?", "sess-1");

        mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("sess-1"))
                .andExpect(jsonPath("$.answer").value("Total is 500"));
    }

    @Test
    void postQuery_withBlankQuery_returnsBadRequest() throws Exception {
        QueryRequest request = new QueryRequest("", null);

        mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSession_returnsCreatedSession() throws Exception {
        SessionResponse sessionResponse = new SessionResponse();
        sessionResponse.setSessionId("new-session");
        sessionResponse.setUserId("user-1");
        when(vertexSessionService.createSession(any(CreateSessionRequest.class))).thenReturn(sessionResponse);

        CreateSessionRequest request = new CreateSessionRequest("user-1", "My Session");

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("new-session"))
                .andExpect(jsonPath("$.userId").value("user-1"));
    }

    @Test
    void createSession_withNoBody_returnsOk() throws Exception {
        SessionResponse sessionResponse = new SessionResponse();
        sessionResponse.setSessionId("auto-session");
        when(vertexSessionService.createSession(any(CreateSessionRequest.class))).thenReturn(sessionResponse);

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("auto-session"));
    }

    @Test
    void listSessions_returnsSessionList() throws Exception {
        SessionResponse s1 = new SessionResponse();
        s1.setSessionId("s1");
        SessionResponse s2 = new SessionResponse();
        s2.setSessionId("s2");
        when(vertexSessionService.listSessions()).thenReturn(List.of(s1, s2));

        mockMvc.perform(get("/api/v1/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sessionId").value("s1"))
                .andExpect(jsonPath("$[1].sessionId").value("s2"));
    }

    @Test
    void getSession_returnsSession() throws Exception {
        SessionResponse session = new SessionResponse();
        session.setSessionId("my-session");
        when(vertexSessionService.getSession("my-session")).thenReturn(session);

        mockMvc.perform(get("/api/v1/sessions/my-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("my-session"));
    }

    @Test
    void deleteSession_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/sessions/my-session"))
                .andExpect(status().isNoContent());

        verify(vertexSessionService).deleteSession("my-session");
    }
}
