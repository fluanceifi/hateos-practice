package com.example.smu_club.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;


@Configuration
public class AppConfig {

    @Value("${external.univ-api.url}")
    private String univApiUrl;

    @Bean
    public RestClient univRestClient() {
        return RestClient.builder()
                .baseUrl(univApiUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
