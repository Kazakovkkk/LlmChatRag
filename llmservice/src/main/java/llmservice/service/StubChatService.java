package llmservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class StubChatService {

    private static final Random RANDOM = new Random();
    private final ObjectMapper objectMapper;

    private static final String STUB_ANSWER =
            "Это тестовый ответ заглушки LLM для нагрузочного тестирования системы бронирования отеля. " +
                    "Все необходимые сервисы и базы данных опрошены успешно.";

    /**
     * Используется и для препроцессинга (ожидает JSON), и для нестримингового ответа.
     * Различаем по содержимому промпта.
     */
    public Mono<String> chat(String prompt) {
        int delay = 200 + RANDOM.nextInt(101); // 200-300 мс

        if (prompt.contains("\"intentType\"")) {
            // Это запрос препроцессора → возвращаем валидный JSON под PreprocessedQuestion
            String question = extractQuestion(prompt);

            Map<String, Object> response = Map.of(
                    "intentType", "SEARCH",
                    "actionName", "",
                    "parameters", Map.of(),
                    "normalized", question,
                    "alternatives", List.of(
                            question + " (вариант 1)",
                            question + " (вариант 2)",
                            question + " (вариант 3)"
                    )
            );

            try {
                String json = objectMapper.writeValueAsString(response);
                return Mono.just(json).delayElement(Duration.ofMillis(delay));
            } catch (Exception e) {
                return Mono.just("{}").delayElement(Duration.ofMillis(delay));
            }
        }

        // Нестриминговый ответ (используется RagChatService.chat -> /llm/answer)
        return Mono.just(STUB_ANSWER).delayElement(Duration.ofMillis(delay));
    }

    /**
     * Стриминговый ответ гостю. Эмулирует генерацию токенов за 200-300 мс суммарно.
     */
    public Flux<String> chatStream(String prompt) {
        String[] words = STUB_ANSWER.split(" ");
        int totalDelay = 200 + RANDOM.nextInt(101);
        int perTokenDelay = Math.max(1, totalDelay / words.length);

        return Flux.fromArray(words)
                .map(w -> w + " ")
                .delayElements(Duration.ofMillis(perTokenDelay));
    }

    private String extractQuestion(String prompt) {
        String marker = "Текущий вопрос пользователя:";
        int idx = prompt.lastIndexOf(marker);
        if (idx >= 0) {
            return prompt.substring(idx + marker.length()).trim();
        }
        return "тестовый запрос";
    }
}