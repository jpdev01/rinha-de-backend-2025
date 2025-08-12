package com.jpdev01.rinha.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.TimeZone;

@Configuration
public class ServerConfig {

    @Value("${services.processor-default}")
    private String processorDefault;

    @Value("${services.processor-fallback}")
    private String processorFallback;

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Bean(name = "defaultWebClient")
    public WebClient webClient() {
        System.out.println("Default WebClient initialized with base URL: " + processorDefault);
        return WebClient.builder()
                .baseUrl(processorDefault)
                .build();
    }

    @Bean(name = "fallbackWebClient")
    public WebClient fallbackWebClient() {
        return WebClient.builder()
                .baseUrl(processorFallback)
                .build();
    }
}