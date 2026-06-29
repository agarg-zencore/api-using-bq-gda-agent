package com.example.bqgdaagent.controller;

import com.example.bqgdaagent.model.CreateSessionRequest;
import com.example.bqgdaagent.model.QueryRequest;
import com.example.bqgdaagent.model.QueryResponse;
import com.example.bqgdaagent.model.SessionResponse;
import com.example.bqgdaagent.service.BigQueryAgentService;
import com.example.bqgdaagent.service.VertexSessionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing endpoints to interact with the BigQuery Gemini Data Agent (BQ GDA)
 * and the Vertex AI Session and Memory Engine.
 */
@RestController
@RequestMapping("/api/v1")
public class ConversationController {

    private final BigQueryAgentService bigQueryAgentService;
    private final VertexSessionService sessionService;

    public ConversationController(BigQueryAgentService bigQueryAgentService,
                                  VertexSessionService sessionService) {
        this.bigQueryAgentService = bigQueryAgentService;
        this.sessionService = sessionService;
    }

    /**
     * Submits a natural language query to the BigQuery Gemini Data Agent.
     * Optionally accepts a {@code sessionId} in the request body to continue
     * an existing multi-turn conversation. If no session ID is provided, a new
     * session is automatically created via the Vertex Session and Memory Engine.
     *
     * @param request the query request
     * @return the agent response and the session ID used
     */
    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        QueryResponse response = bigQueryAgentService.query(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Creates a new conversation session in the Vertex AI Session and Memory Engine.
     *
     * @param request optional session metadata (userId, displayName)
     * @return the created session details including the session ID
     */
    @PostMapping("/sessions")
    public ResponseEntity<SessionResponse> createSession(
            @RequestBody(required = false) CreateSessionRequest request) {
        if (request == null) {
            request = new CreateSessionRequest();
        }
        SessionResponse session = sessionService.createSession(request);
        return ResponseEntity.ok(session);
    }

    /**
     * Lists all sessions in the Vertex AI Session and Memory Engine.
     *
     * @return a list of session details
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> listSessions() {
        List<SessionResponse> sessions = sessionService.listSessions();
        return ResponseEntity.ok(sessions);
    }

    /**
     * Retrieves a specific session by its ID.
     *
     * @param sessionId the session ID
     * @return the session details
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable String sessionId) {
        SessionResponse session = sessionService.getSession(sessionId);
        return ResponseEntity.ok(session);
    }

    /**
     * Deletes a session from the Vertex AI Session and Memory Engine.
     *
     * @param sessionId the session ID to delete
     * @return 204 No Content on success
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        sessionService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }
}
