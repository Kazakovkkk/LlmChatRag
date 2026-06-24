package embeddingservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RosbertaClientConfig {

    @Bean
    public RestClient ruEnHuggingFaceRestClient(
            @Value("${huggingface.tei.url:http://localhost:8081}")
            String huggingFaceTeiUrl
    ) {
        return RestClient.builder()
                .baseUrl(huggingFaceTeiUrl)
                .build();
    }
}