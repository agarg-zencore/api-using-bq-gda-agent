package com.zencore.bqagent;

import com.zencore.bqagent.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class BqAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(BqAgentApplication.class, args);
    }
}
