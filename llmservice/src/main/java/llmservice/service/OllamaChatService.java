package llmservice.service;

import llmservice.dto.MessageDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
//ollama run gemma3:4b
@Service
public class OllamaChatService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OllamaChatService(@Qualifier("ollamaWebClient") WebClient webClient,
                             ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }
    // Стриминг — токен за токеном
    public Flux<String> chatStream(String prompt, List<MessageDto> history) {
        List<Map<String, String>> messages = new ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content", "Ты ассистент отеля. Отвечай на том же языке на котором задан вопрос. Даавай ответ, если он есть в контексте"
        ));

        // ← НЕ добавляем историю в messages[]
        // История уже передана в промпте через AG_SYSTEM_PROMPT

        messages.add(Map.of("role", "user", "content", prompt));

        Map<String, Object> request = Map.of(
                "model", "gemma3:4b",
                "messages", messages,
                "stream", true
        );

        return webClient.post()
                .uri("/api/chat")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::parseToken);
    }

    // Обычный запрос без стриминга (оставим для препроцессинга)
    public Mono<String> chat(String prompt) {
        Map<String, Object> request = Map.of(
                "model", "gemma3:4b",
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "stream", false
        );

        return webClient.post()
                .uri("/api/chat")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    try {
                        JsonNode node = objectMapper.readTree(response);
                        return node.get("message").get("content").asText();
                    } catch (Exception e) {
                        throw new RuntimeException("Ошибка парсинга: " + e.getMessage());
                    }
                });
    }

    private Flux<String> parseToken(String chunk) {
        try {
            JsonNode node = objectMapper.readTree(chunk);
            // Ollama возвращает {"message":{"content":"токен"}, "done": false}
            boolean done = node.path("done").asBoolean(false);
            if (done) return Flux.empty();
            String token = node.path("message").path("content").asText("");
            return Flux.just(token);
        } catch (Exception e) {
            return Flux.empty();
        }
    }
}