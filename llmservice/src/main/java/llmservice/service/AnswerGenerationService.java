package llmservice.service;

import llmservice.dto.MessageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class AnswerGenerationService {

    private static final String AG_SYSTEM_PROMPT = """
    Ты ассистент отеля который отвечает на вопросы гостей.
    
    На вход получаешь:
    1) Текущее время запроса.
    2) Историю беседы.
    3) Вопрос пользователя.
    4) Контекст из базы знаний отеля.
    
    ПРАВИЛА:
    - ВАЖНО: отвечай на том же языке на котором задан вопрос пользователя.
      Если вопрос на русском — отвечай на русском.
      Если вопрос на английском — отвечай на английском.
      Если вопрос на другом языке — отвечай на том же языке.
    - Используй ТОЛЬКО информацию из контекста, другую информацию нельзя использовать.
    - Если вопрос 'а после него?' или 'во сколько?' — используй историю беседы
      чтобы понять о чём речь, и дай конкретный ответ с временем.
    - Никогда не смешивай языки в одном ответе.
    """;

    private final LlmRouter llmRouter;

    // Стриминг токенов
    public Flux<String> generateAnswerStream(String userQuestion,
                                             String context,
                                             List<MessageDto> history,
                                             String timestamp) {
        String timeContext = "";
        if (timestamp != null) {
            try {
                ZonedDateTime zdt = ZonedDateTime.parse(timestamp)
                        .withZoneSameInstant(ZoneId.of("Europe/Moscow"));
                String formattedTime = zdt.format(
                        DateTimeFormatter.ofPattern("HH:mm, EEEE", new Locale("ru")));
                timeContext = "Текущее время: " + formattedTime + "\n\n";
            } catch (Exception e) {
                timeContext = "Текущее время: " + timestamp + "\n\n";
            }
        }

        // История в текстовом виде
        String historyContext = "";
        if (history != null && !history.isEmpty()) {
            historyContext = "История беседы:\n" +
                    history.stream()
                            .map(m -> (m.getRole().equals("user") ? "Гость" : "Ассистент")
                                    + ": " + m.getContent())
                            .collect(Collectors.joining("\n"))
                    + "\n\n";
        }

        String prompt = AG_SYSTEM_PROMPT + "\n\n"
                + timeContext
                + historyContext  // ← история в промпте
                + "Текущий вопрос гостя: " + userQuestion + "\n\n"
                + "Контекст из базы знаний:\n'''\n" + context + "\n'''\n\n"
                + "ВАЖНО: контекст из базы знаний является истиной. "
                + "Если история беседы противоречит контексту — доверяй контексту.";

        log.info("=== ПРОМПТ ДЛЯ LLM ===");
        log.info("Время: {}", timeContext.trim());
        log.info("Вопрос: {}", userQuestion);
        log.info("Контекст: {}", context);
        log.info("История ({} сообщений):", history != null ? history.size() : 0);

        return llmRouter.chatStream(prompt, history);
    }

    // Обычный ответ (оставим на всякий случай)
    public Mono<String> generateAnswer(String userQuestion, String context) {
        String prompt = AG_SYSTEM_PROMPT + "\n\n"
                + "Вопрос пользователя: " + userQuestion + "\n\n"
                + "Контекст документов:\n'''\n" + context + "\n'''";

        return llmRouter.chat(prompt);
    }

}