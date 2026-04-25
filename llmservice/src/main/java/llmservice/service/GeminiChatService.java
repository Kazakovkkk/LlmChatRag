package llmservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import llmservice.dto.MessageDto;
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
public class GeminiChatService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiChatService(
            @Qualifier("geminiWebClient") WebClient webClient,
            ObjectMapper objectMapper,
            @Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.model:gemini-2.0-flash}") String model) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    // Обычный запрос (для препроцессинга)
    public Mono<String> chat(String prompt) {
        Map<String, Object> request = buildRequest(prompt, List.of(), false);
        log.info("Gemini chat | Модель: {}", model);

        return webClient.post()
                .uri("/v1beta/models/" + model + ":generateContent?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseResponse);
    }

    // Стриминг (для генерации ответа)
    public Flux<String> chatStream(String prompt, List<MessageDto> history) {
        Map<String, Object> request = buildRequest(prompt, history, true);
        log.info("Gemini chat | Модель: {}", model);
        return webClient.post()
                .uri("/v1beta/models/" + model + ":streamGenerateContent?alt=sse&key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::parseStreamToken)
                .filter(token -> !token.isEmpty());
    }

    private Map<String, Object> buildRequest(String prompt,
                                             List<MessageDto> history,
                                             boolean stream) {
        List<Map<String, Object>> contents = new ArrayList<>();

        // Добавляем историю
        if (history != null) {
            for (MessageDto msg : history) {
                String geminiRole = msg.getRole().equals("assistant") ? "model" : "user";
                contents.add(Map.of(
                        "role", geminiRole,
                        "parts", List.of(Map.of("text", msg.getContent()))
                ));
            }
        }

        // Текущий промпт
        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", prompt))
        ));

        return Map.of(
                "contents", contents,
                "generationConfig", Map.of(
                        "temperature", 0.7,
                        "maxOutputTokens", 1024
                )
        );
    }

    private String parseResponse(String response) {
        try {
            JsonNode node = objectMapper.readTree(response);
            return node.path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();
        } catch (Exception e) {
            log.error("Ошибка парсинга Gemini ответа: {}", e.getMessage());
            return "";
        }
    }

    private Flux<String> parseStreamToken(String chunk) {
        try {
            // SSE формат: "data: {...}"
            String json = chunk.startsWith("data:") ? chunk.substring(5).trim() : chunk;
            if (json.isEmpty() || json.equals("[DONE]")) return Flux.empty();

            JsonNode node = objectMapper.readTree(json);
            String token = node.path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText("");

            return Flux.just(token);
        } catch (Exception e) {
            return Flux.empty();
        }
    }
}