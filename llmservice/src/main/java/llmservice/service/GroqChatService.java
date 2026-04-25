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
public class GroqChatService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private double temperature = 0.7;

    public GroqChatService(
            @Qualifier("groqWebClient") WebClient webClient,
            ObjectMapper objectMapper,
            @Value("${groq.api-key}") String apiKey,
            @Value("${groq.model:llama-3.1-8b-instant}") String model) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    public Mono<String> chat(String prompt) {
        log.info("Groq chat | Модель: {}", model);

        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", prompt)
        );

        Map<String, Object> request = Map.of(
                "model", model,
                "messages", messages,
                "stream", false
        );

        return webClient.post()
                .uri("/openai/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseResponse);
    }

    public Flux<String> chatStream(String prompt, List<MessageDto> history) {
        log.info("Groq stream | Модель: {} | История: {} сообщений",
                model, history != null ? history.size() : 0);

        List<Map<String, String>> messages = new ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content", "Ты ассистент отеля. Отвечай на том же языке на котором задан вопрос. Отвечай только ответ есть в контексте"
        ));

        if (history != null) {
            history.forEach(msg -> messages.add(Map.of(
                    "role", msg.getRole().equals("assistant") ? "assistant" : "user",
                    "content", msg.getContent()
            )));
        }

        messages.add(Map.of("role", "user", "content", prompt));

        Map<String, Object> request = Map.of(
                "model", model,
                "messages", messages,
                "stream", true,
                "temperature", temperature
        );

        return webClient.post()
                .uri("/openai/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::parseStreamToken);
    }

    private String parseResponse(String response) {
        try {
            JsonNode node = objectMapper.readTree(response);
            return node.path("choices").get(0)
                    .path("message").path("content").asText();
        } catch (Exception e) {
            log.error("Ошибка парсинга Groq ответа: {}", e.getMessage());
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