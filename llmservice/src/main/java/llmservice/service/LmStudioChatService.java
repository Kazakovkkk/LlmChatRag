package llmservice.service;

import llmservice.dto.MessageDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LmStudioChatService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public LmStudioChatService(
            @Qualifier("lmStudioWebClient") WebClient webClient,
            ObjectMapper objectMapper,
            @Value("${lmstudio.model:qwen3.5-9b-uncensored-hauhaucs-aggressive}")
            String model) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    public Mono<String> chat(String prompt) {
        log.info("LM Studio chat | Модель: {}", model);

        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", prompt)
        );

        Map<String, Object> request = Map.of(
                "model", model,
                "messages", messages,
                "stream", false,
                "temperature", 0.7
        );

        return webClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseResponse)
                .doOnError(e -> log.error("LM Studio ошибка: {}", e.getMessage()));
    }

    public Flux<String> chatStream(String prompt) {
        log.info("LM Studio stream | Модель: {}",
                model);

        List<Map<String, String>> messages = new ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content", "Ты ассистент отеля. Отвечай на том же языке на котором задан вопрос. Отвечай только если ответ есть в контексте."
        ));
        messages.add(Map.of("role", "user", "content", prompt));

        Map<String, Object> request = Map.of(
                "model", model,
                "messages", messages,
                "stream", true,
                "temperature", 0.7
        );

        return webClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::parseStreamToken)
                .doOnComplete(() -> log.info("LM Studio stream завершён"))
                .doOnError(e -> log.error("LM Studio stream ошибка: {}", e.getMessage()));
    }

    private String parseResponse(String response) {
        try {
            JsonNode node = objectMapper.readTree(response);
            return node.path("choices").get(0)
                    .path("message").path("content").asText();
        } catch (Exception e) {
            log.error("Ошибка парсинга LM Studio ответа: {}", e.getMessage());
            return "";
        }
    }

    private Flux<String> parseStreamToken(String chunk) {
        try {
            String json = chunk.startsWith("data:") ? chunk.substring(5).trim() : chunk;
            if (json.isEmpty() || json.equals("[DONE]")) return Flux.empty();

            JsonNode node = objectMapper.readTree(json);
            JsonNode delta = node.path("choices").get(0).path("delta");
            if (delta.has("content")) {
                return Flux.just(delta.path("content").asText(""));
            }
            return Flux.empty();
        } catch (Exception e) {
            return Flux.empty();
        }
    }
}