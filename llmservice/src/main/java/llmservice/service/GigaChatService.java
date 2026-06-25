package llmservice.service;

import llmservice.dto.MessageDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class GigaChatService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String authKey;
    private final String model;
    private final String scope;
    private final AtomicReference<Mono<String>> tokenMonoRef = new AtomicReference<>();



    private String cachedToken = null;
    private long tokenExpiresAt = 0;

    public GigaChatService(
            @Qualifier("gigachatWebClient") WebClient webClient,
            ObjectMapper objectMapper,
            @Value("${gigachat.auth-key}") String authKey,
            @Value("${gigachat.model:GigaChat}") String model,
            @Value("${gigachat.scope:GIGACHAT_API_PERS}") String scope) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.authKey = authKey;
        this.model = model;
        this.scope = scope;
    }


    private Mono<String> getAccessToken() {
        return tokenMonoRef.updateAndGet(existing ->
                existing != null ? existing : createCachedTokenMono()
        );
    }

    private Mono<String> createCachedTokenMono() {
        Mono<String> tokenMono = Mono.defer(() -> {
            return webClient.post()
                    .uri("https://ngw.devices.sberbank.ru:9443/api/v2/oauth")
                    .header("Authorization", "Basic " + authKey)
                    .header("RqUID", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("scope", scope))
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(response -> {
                        try {
                            JsonNode node = objectMapper.readTree(response);
                            String token = node.path("access_token").asText();

                            return token;
                        } catch (Exception e) {
                            throw new RuntimeException("Ошибка парсинга токена GigaChat: " + e.getMessage());
                        }
                    });
        });

        return tokenMono.cache(
                        value -> Duration.ofMinutes(25),
                        error -> Duration.ZERO,
                        () -> Duration.ZERO
                )
                .doOnError(e -> {
                    tokenMonoRef.set(null);
                    log.error("Ошибка получения OAuth токена GigaChat: {}", e.getMessage());
                });
    }

    public Mono<String> chat(String prompt) {
        return getAccessToken().flatMap(token -> {
            //log.info("GigaChat chat | Модель: {}", model);

            List<Map<String, String>> messages = List.of(
                    Map.of("role", "user", "content", prompt)
            );

            Map<String, Object> request = Map.of(
                    "model", model,
                    "messages", messages,
                    "stream", false
            );

            return webClient.post()
                    .uri("https://gigachat.devices.sberbank.ru/api/v1/chat/completions")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(this::parseResponse);
        });
    }

    public Flux<String> chatStream(String prompt) {
        return getAccessToken().flatMapMany(token -> {
            //log.info("GigaChat stream | Модель: {}", model);

            List<Map<String, String>> messages = List.of(
                    Map.of("role", "user", "content", prompt)
            );

            Map<String, Object> request = Map.of(
                    "model", model,
                    "messages", messages,
                    "stream", true,
                    "temperature", 0.7
            );

            return webClient.post()
                    .uri("https://gigachat.devices.sberbank.ru/api/v1/chat/completions")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .flatMap(this::parseStreamToken);
        });
    }

    private String parseResponse(String response) {
        try {
            JsonNode node = objectMapper.readTree(response);
            return node.path("choices").get(0)
                    .path("message").path("content").asText();
        } catch (Exception e) {
            log.error("Ошибка парсинга GigaChat ответа: {}", e.getMessage());
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