package llmservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class LmStudioClientConfig {

    @Value("${lmstudio.url:http://localhost:1234}")
    private String lmStudioUrl;

    @Bean
    public WebClient lmStudioWebClient() {
        return WebClient.builder()
                .baseUrl(lmStudioUrl)
                .build();
    }
}