package com.zencore.bqagent.client;

import com.zencore.bqagent.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class VertexAiPlatformClient {

    private final AppProperties properties;
    private final GoogleCredentials credentials;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public VertexAiPlatformClient(
            AppProperties properties,
            GoogleCredentials credentials,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.credentials = credentials;
        this.objectMapper = objectMapper;
    }

    public JsonNode get(String resourcePath) throws IOException, InterruptedException {
        return send("GET", resourcePath, null);
    }

    public JsonNode post(String resourcePath, JsonNode body) throws IOException, InterruptedException {
        return send("POST", resourcePath, body);
    }

    public JsonNode delete(String resourcePath) throws IOException, InterruptedException {
        return send("DELETE", resourcePath, null);
    }

    public JsonNode postAction(String resourcePath, String action, JsonNode body)
            throws IOException, InterruptedException {
        return send("POST", resourcePath + ":" + action, body);
    }

    public void pollOperation(JsonNode operation) throws IOException, InterruptedException {
        if (operation == null || operation.path("done").asBoolean(false)) {
            return;
        }
        String operationName = operation.path("name").asText(null);
        if (operationName == null || operationName.isBlank()) {
            return;
        }
        for (int attempt = 0; attempt < 20; attempt++) {
            Thread.sleep(500L * (attempt + 1));
            JsonNode status = get("/" + operationName);
            if (status.path("done").asBoolean(false)) {
                if (status.has("error")) {
                    throw new IOException("Vertex operation failed: " + status.get("error"));
                }
                return;
            }
        }
        throw new IOException("Timed out waiting for Vertex operation: " + operationName);
    }

    private JsonNode send(String method, String resourcePath, JsonNode body)
            throws IOException, InterruptedException {
        credentials.refreshIfExpired();
        String token = credentials.getAccessToken().getTokenValue();

        String url = properties.vertexApiBaseUrl() + resourcePath;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + token);

        if (body != null) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        } else if ("DELETE".equals(method)) {
            builder.DELETE();
        } else {
            builder.GET();
        }

        HttpResponse<String> response =
                httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new IOException(
                    "Vertex API " + method + " " + resourcePath + " failed: "
                            + response.statusCode() + " " + response.body());
        }
        if (response.body() == null || response.body().isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(response.body());
    }
}
