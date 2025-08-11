package com.jpdev01.rinha.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.TimeZone;

@Configuration
public class ServerConfig {

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Bean(name = "defaultWebClient")
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl("http://localhost:8001")
                .build();
    }

    @Bean(name = "fallbackWebClient")
    public WebClient fallbackWebClient() {
        return WebClient.builder()
                .baseUrl("http://localhost:8002")
                .build();
    }
}