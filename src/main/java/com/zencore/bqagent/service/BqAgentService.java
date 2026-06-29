package com.zencore.bqagent.service;

import com.zencore.bqagent.config.AppProperties;
import com.zencore.bqagent.dto.ChatMessageResponse;
import com.zencore.bqagent.dto.ConversationResponse;
import com.zencore.bqagent.dto.MemoryListResponse;
import com.zencore.bqagent.dto.MessageListResponse;
import com.zencore.bqagent.dto.SessionMessageDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ServerStream;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.geminidataanalytics.v1beta.ChatRequest;
import com.google.cloud.geminidataanalytics.v1beta.DataAgentContext;
import com.google.cloud.geminidataanalytics.v1beta.DataChatServiceClient;
import com.google.cloud.geminidataanalytics.v1beta.DataChatServiceSettings;
import com.google.cloud.geminidataanalytics.v1beta.Message;
import com.google.cloud.geminidataanalytics.v1beta.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.Executor;

@Service
public class BqAgentService {

    private static final Logger log = LoggerFactory.getLogger(BqAgentService.class);

    private final AppProperties properties;
    private final GoogleCredentials credentials;
    private final MessageParser messageParser;
    private final VertexSessionService vertexSessionService;
    private final VertexMemoryBankService vertexMemoryBankService;
    private final SessionEventMapper sessionEventMapper;
    private final Executor asyncExecutor;

    public BqAgentService(
            AppProperties properties,
            GoogleCredentials credentials,
            MessageParser messageParser,
            VertexSessionService vertexSessionService,
            VertexMemoryBankService vertexMemoryBankService,
            SessionEventMapper sessionEventMapper,
            Executor sseExecutor) {
        this.properties = properties;
        this.credentials = credentials;
        this.messageParser = messageParser;
        this.vertexSessionService = vertexSessionService;
        this.vertexMemoryBankService = vertexMemoryBankService;
        this.sessionEventMapper = sessionEventMapper;
        this.asyncExecutor = sseExecutor;
    }

    public ConversationResponse createConversation(String conversationId, String userId)
            throws IOException, InterruptedException {
        String sessionId = vertexSessionService.createSession(userId, conversationId);
        String resolvedUserId = userId != null && !userId.isBlank()
                ? userId
                : properties.resolvedDefaultUserId();
        String sessionResourceName = vertexSessionService.sessionResourceName(sessionId);
        return new ConversationResponse(sessionId, resolvedUserId, sessionResourceName);
    }

    public void deleteConversation(String conversationId) throws IOException, InterruptedException {
        vertexSessionService.deleteSession(conversationId);
    }

    public MessageListResponse listMessages(String conversationId)
            throws IOException, InterruptedException {
        List<JsonNode> events = vertexSessionService.listEvents(conversationId);
        List<SessionMessageDto> messages = new ArrayList<>();
        for (JsonNode event : events) {
            String text = sessionEventMapper.extractText(event);
            if (text == null || text.isBlank()) {
                continue;
            }
            messages.add(new SessionMessageDto(
                    event.path("author").asText("user"),
                    text,
                    event.path("invocationId").asText(null),
                    event.path("timestamp").asText(null)));
        }
        return new MessageListResponse(conversationId, messages);
    }

    public MemoryListResponse listMemories(String userId) throws IOException, InterruptedException {
        String resolvedUserId = userId != null && !userId.isBlank()
                ? userId
                : properties.resolvedDefaultUserId();
        return new MemoryListResponse(
                resolvedUserId,
                vertexMemoryBankService.listMemories(resolvedUserId));
    }

    public List<ChatMessageResponse> conversationChat(
            String conversationId,
            String userId,
            String userMessage)
            throws IOException, InterruptedException {
        List<ChatMessageResponse> responses = new ArrayList<>();
        conversationChatStream(conversationId, userId, userMessage, responses::add);
        return responses;
    }

    public void conversationChatStream(
            String conversationId,
            String userId,
            String userMessage,
            Consumer<ChatMessageResponse> consumer)
            throws IOException, InterruptedException {
        String resolvedUserId = resolveUserId(conversationId, userId);
        String invocationId = UUID.randomUUID().toString();

        List<JsonNode> events = vertexSessionService.listEvents(conversationId);
        List<String> memories = vertexMemoryBankService.retrieveMemories(resolvedUserId, userMessage);
        String memoryContext = vertexMemoryBankService.formatMemoriesContext(memories);

        List<Message> history = sessionEventMapper.toGdaMessages(events);
        String effectiveMessage = buildEffectiveUserMessage(userMessage, memoryContext);

        ChatRequest.Builder requestBuilder = ChatRequest.newBuilder()
                .setParent(properties.parent())
                .setDataAgentContext(DataAgentContext.newBuilder()
                        .setDataAgent(dataAgentResourceName())
                        .build());
        history.forEach(requestBuilder::addMessages);
        requestBuilder.addMessages(Message.newBuilder()
                .setUserMessage(UserMessage.newBuilder().setText(effectiveMessage).build())
                .build());

        log.info(
                "Conversation chat: conversationId={} historyEvents={} memories={}",
                conversationId,
                events.size(),
                memories.size());

        List<ChatMessageResponse> collected = new ArrayList<>();
        try (DataChatServiceClient chatClient = createChatClient()) {
            ServerStream<Message> stream = chatClient.chatCallable().call(requestBuilder.build());
            for (Message response : stream) {
                ChatMessageResponse parsed = messageParser.parse(response);
                collected.add(parsed);
                consumer.accept(parsed);
            }
        }

        vertexSessionService.appendUserMessage(
                conversationId, resolvedUserId, userMessage, invocationId);

        String agentText = messageParser.extractFinalResponseText(collected);
        if (agentText != null && !agentText.isBlank()) {
            vertexSessionService.appendAgentMessage(conversationId, agentText, invocationId);
        }

        generateMemoriesAsync(conversationId, resolvedUserId);
    }

    public List<ChatMessageResponse> chat(String userMessage) throws IOException {
        List<ChatMessageResponse> responses = new ArrayList<>();
        chatStream(userMessage, responses::add);
        return responses;
    }

    public void chatStream(String userMessage, Consumer<ChatMessageResponse> consumer)
            throws IOException {
        log.info("Stateless chat: agent={}", dataAgentResourceName());

        Message message = Message.newBuilder()
                .setUserMessage(UserMessage.newBuilder().setText(userMessage).build())
                .build();

        ChatRequest request = ChatRequest.newBuilder()
                .setParent(properties.parent())
                .addMessages(message)
                .setDataAgentContext(DataAgentContext.newBuilder()
                        .setDataAgent(dataAgentResourceName())
                        .build())
                .build();

        try (DataChatServiceClient chatClient = createChatClient()) {
            ServerStream<Message> stream = chatClient.chatCallable().call(request);
            for (Message response : stream) {
                consumer.accept(messageParser.parse(response));
            }
        }
    }

    private String resolveUserId(String conversationId, String userId)
            throws IOException, InterruptedException {
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        try {
            return vertexSessionService.getUserId(conversationId);
        } catch (IOException ex) {
            return properties.resolvedDefaultUserId();
        }
    }

    private String buildEffectiveUserMessage(String userMessage, String memoryContext) {
        if (memoryContext == null || memoryContext.isBlank()) {
            return userMessage;
        }
        return memoryContext + "\n\nUser question: " + userMessage;
    }

    private void generateMemoriesAsync(String conversationId, String userId) {
        asyncExecutor.execute(() -> {
            try {
                List<JsonNode> events = vertexSessionService.listEvents(conversationId);
                vertexMemoryBankService.generateMemoriesFromEvents(events, userId);
            } catch (Exception ex) {
                log.warn("Memory generation failed for conversationId={}: {}", conversationId, ex.getMessage());
            }
        });
    }

    private String dataAgentResourceName() {
        return properties.parent() + "/dataAgents/" + properties.dataAgentId();
    }

    private DataChatServiceClient createChatClient() throws IOException {
        DataChatServiceSettings settings = DataChatServiceSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build();
        return DataChatServiceClient.create(settings);
    }
}
