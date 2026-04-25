package LlmChatRag.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Configuration
public class ServicesClientConfig {

    @Value("${qdrant.service.url:http://localhost:8085}")
    private String qdrantServiceUrl;

    @Value("${llm.service.url:http://localhost:8083}")
    private String llmServiceUrl;

    @Bean
    public RestClient qdrantRestClient() {
        log.info("=== Qdrant URL: '{}' ===", qdrantServiceUrl);
        return RestClient.builder()
                .baseUrl(qdrantServiceUrl)
                .build();
    }

    @Bean
    public RestClient llmRestClient() {
        return RestClient.builder()
                .baseUrl(llmServiceUrl)
                .build();
    }
    @Bean
    public WebClient llmWebClient() {
        return WebClient.builder()
                .baseUrl(llmServiceUrl)
                .build();
    }
}