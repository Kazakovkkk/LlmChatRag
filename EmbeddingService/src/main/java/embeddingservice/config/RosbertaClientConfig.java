package embeddingservice.config;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RosbertaClientConfig {
    // Локальный URL Docker контейнера
    // docker run -p 8081:80 -e MODEL_ID=intfloat/multilingual-e5-base ghcr.io/huggingface/text-embeddings-inference:cpu-latest
    String rosbertaUrl = "http://localhost:8081";

    @Bean
    public RestClient ruEnHuggingFaceRestClient() {
        return RestClient.builder()
                .baseUrl(rosbertaUrl)
                .build();
    }
}