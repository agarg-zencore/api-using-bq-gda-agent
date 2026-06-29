package com.zencore.bqagent.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.List;

@Configuration
public class GcpConfig {

    @Bean
    GoogleCredentials sourceCredentials() throws IOException {
        return GoogleCredentials.getApplicationDefault()
                .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
    }

    @Bean
    BigQuery bigQuery(GoogleCredentials sourceCredentials, AppProperties properties) {
        return BigQueryOptions.newBuilder()
                .setProjectId(properties.gcpProjectId())
                .setCredentials(sourceCredentials)
                .build()
                .getService();
    }
}
