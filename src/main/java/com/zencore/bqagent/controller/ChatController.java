package com.zencore.bqagent.controller;

import com.zencore.bqagent.config.AppProperties;
import com.zencore.bqagent.dto.ChatMessageRequest;
import com.zencore.bqagent.dto.ChatMessageResponse;
import com.zencore.bqagent.dto.ConversationChatRequest;
import com.zencore.bqagent.dto.ConversationResponse;
import com.zencore.bqagent.dto.CreateConversationRequest;
import com.zencore.bqagent.dto.FeedbackResponse;
import com.zencore.bqagent.dto.MemoryListResponse;
import com.zencore.bqagent.dto.MessageListResponse;
import com.zencore.bqagent.dto.SubmitFeedbackRequest;
import com.zencore.bqagent.service.AgentFeedbackService;
import com.zencore.bqagent.service.BqAgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private final BqAgentService bqAgentService;
    private final AgentFeedbackService agentFeedbackService;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final Executor sseExecutor;

    public ChatController(
            BqAgentService bqAgentService,
            AgentFeedbackService agentFeedbackService,
            AppProperties properties,
            ObjectMapper objectMapper,
            Executor sseExecutor) {
        this.bqAgentService = bqAgentService;
        this.agentFeedbackService = agentFeedbackService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.sseExecutor = sseExecutor;
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse createConversation(
            @RequestBody(required = false) CreateConversationRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId)
            throws IOException, InterruptedException {
        String userId = body != null && body.userId() != null ? body.userId() : headerUserId;
        String conversationId = body != null ? body.conversationId() : null;
        return bqAgentService.createConversation(conversationId, userId);
    }

    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(@PathVariable String conversationId)
            throws IOException, InterruptedException {
        bqAgentService.deleteConversation(conversationId);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public MessageListResponse listMessages(@PathVariable String conversationId)
            throws IOException, InterruptedException {
        return bqAgentService.listMessages(conversationId);
    }

    @GetMapping("/users/{userId}/memories")
    public MemoryListResponse listMemories(@PathVariable String userId)
            throws IOException, InterruptedException {
        return bqAgentService.listMemories(userId);
    }

    @PostMapping("/conversations/{conversationId}/feedback")
    public FeedbackResponse submitFeedback(
            @PathVariable String conversationId,
            @Valid @RequestBody SubmitFeedbackRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        return agentFeedbackService.submitFeedback(conversationId, body, headerUserId);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public List<ChatMessageResponse> conversationChat(
            @PathVariable String conversationId,
            @Valid @RequestBody ConversationChatRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId)
            throws IOException, InterruptedException {
        String userId = body.userId() != null ? body.userId() : headerUserId;
        return bqAgentService.conversationChat(conversationId, userId, body.message());
    }

    @PostMapping(
            value = "/conversations/{conversationId}/messages/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter conversationChatStream(
            @PathVariable String conversationId,
            @Valid @RequestBody ConversationChatRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        String userId = body.userId() != null ? body.userId() : headerUserId;
        SseEmitter emitter = new SseEmitter(properties.chatTimeoutSeconds() * 1000L);

        sseExecutor.execute(() -> {
            try {
                bqAgentService.conversationChatStream(
                        conversationId, userId, body.message(), chunk -> sendSse(emitter, chunk));
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
            } catch (Exception ex) {
                try {
                    emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(
                            Map.of("error", ex.getMessage(), "status", 502))));
                } catch (IOException ignored) {
                    // fall through
                }
                emitter.completeWithError(ex);
            }
        });

        return emitter;
    }

    @PostMapping("/chat")
    public List<ChatMessageResponse> chat(@Valid @RequestBody ChatMessageRequest body)
            throws IOException {
        return bqAgentService.chat(body.message());
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatMessageRequest body) {
        SseEmitter emitter = new SseEmitter(properties.chatTimeoutSeconds() * 1000L);

        sseExecutor.execute(() -> {
            try {
                bqAgentService.chatStream(body.message(), chunk -> sendSse(emitter, chunk));
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
            } catch (Exception ex) {
                try {
                    emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(
                            Map.of("error", ex.getMessage(), "status", 502))));
                } catch (IOException ignored) {
                    // fall through
                }
                emitter.completeWithError(ex);
            }
        });

        return emitter;
    }

    private void sendSse(SseEmitter emitter, ChatMessageResponse chunk) {
        try {
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(chunk)));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }
}
