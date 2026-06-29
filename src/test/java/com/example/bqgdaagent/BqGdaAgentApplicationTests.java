package com.example.bqgdaagent;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.Date;

import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
        "gcp.project-id=test-project",
        "gcp.location=us-central1",
        "vertex.ai.reasoning-engine-id=test-engine-id"
})
class BqGdaAgentApplicationTests {

    @MockBean
    GoogleCredentials googleCredentials;

    @Test
    void contextLoads() {
        when(googleCredentials.getAccessToken()).thenReturn(
                new AccessToken("test-token", new Date(System.currentTimeMillis() + 3600_000)));
        // Verifies that the Spring application context loads successfully
    }
}
