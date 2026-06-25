package qdrantservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class EmbeddingClientConfig {

    @Value("${embedding.service.url:http://localhost:8084}")
    private String embeddingServiceUrl;

    @Bean
    public RestClient embeddingRestClient() {
        return RestClient.builder()
                .baseUrl(embeddingServiceUrl)
                .build();
    }
}