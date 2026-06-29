package com.example.bqgdaagent.config;

import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

@Configuration
public class AppConfig {

    @Value("${vertex.ai.base-url:https://us-central1-aiplatform.googleapis.com}")
    private String vertexAiBaseUrl;

    @Bean
    public GoogleCredentials googleCredentials() throws IOException {
        return GoogleCredentials.getApplicationDefault()
                .createScoped("https://www.googleapis.com/auth/cloud-platform");
    }

    @Bean
    public WebClient vertexAiWebClient() {
        return WebClient.builder()
                .baseUrl(vertexAiBaseUrl)
                .build();
    }
}
