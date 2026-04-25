package llmservice.service;

import llmservice.dto.MessageDto;
import llmservice.dto.PreprocessedQuestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class QueryPreprocessorService {

    private static final String PREPROCESSED_SYSTEM_PROMPT =
            "Ты специалист по семантической оптимизации пользовательских вопросов для поиска по эмбеддингам. "
                    + "На вход получаешь вопрос пользователя и историю беседы"
                    + "Преобразуй входной ВОПРОС по правилам:\n"
                    + "1) Нормализация:\n"
                    + "   - Удали спецсимволы (оставь только !?.,)\n"
                    + "   - Убери лишние пробелы и переносы строк\n"
                    + "   - Приведи все кавычки к виду \\\"\\\"\n"
                    + "   - Расшифруй сокращения: «н-р» → «например», «т.д.» → «и так далее»\n"
                    + " - Удаляй любые смайлики из вопроса "
                    + "2) Семантическое уплотнение:\n"
                    + "   - Сохрани ключевые термины, числа, имена собственные без изменений\n"
                    + "   - Удали стоп-слова («очень», «просто», «ну») и вводные фразы («кстати», «в общем»)\n"
                    + "   - Устрани повторы, сделай формулировку точной и ёмкой\n"
                    + "   - Заменяй местоимения на конкретные референсы (напр. «он» → «алгоритм авторизации»)\n"
                    + "3) Контекстуализация:\n"
                    + "   - Добавь недостающие уточнения в [квадратных скобках], если это повышает однозначность\n"
                    + "   - Делай вопрос самодостаточным: «Как он работает?» → «Как работает алгоритм авторизации?»\n"
                    + "Формат вывода:\n"
                    + "   - Сначала выведи ТОЛЬКО итоговый очищенный и уточнённый вопрос (без комментариев)\n"
                    + "   - Если вопрос состоит из нескольких смысловых частей, раздели их пустой строкой\n"
                    + "   - Затем выведи 3 альтернативные формулировки, основанные на вопросе пользователя и его истории беседы, сохраняя смысл:\n"
                    + "Вывод ТОЛЬКО в JSON с полями:\n"
                    + "{\\\"normalized\\\": \\\"<строка>\\\",\\\"alternatives\\\": [\\\"<строка>\\\",\\\"<строка>\\\",\\\"<строка>\\\"]}"
                    + "Без пояснений и текста вне JSON";


    private final LlmRouter llmRouter;
    private final ObjectMapper objectMapper;

    public QueryPreprocessorService(LlmRouter llmRouter, ObjectMapper objectMapper) {
        this.llmRouter = llmRouter;
        this.objectMapper = objectMapper;
    }

    public Mono<PreprocessedQuestion> preprocessQuestion(String question,
                                                         List<MessageDto> history) {
        String historyContext = "";
        if (history != null && !history.isEmpty()) {
            historyContext = "История беседы:\n" +
                    history.stream()
                            .map(m -> m.getRole() + ": " + m.getContent())
                            .collect(Collectors.joining("\n"))
                    + "\n\n";
        }

        String prompt = historyContext + PREPROCESSED_SYSTEM_PROMPT + "\n\nВопрос: " + question;

        return llmRouter.chat(prompt)
                .map(raw -> extractVariants(raw, question));
    }

    private PreprocessedQuestion extractVariants(String rawResponse, String originalQuestion) {
        try {
            return objectMapper.readValue(rawResponse, PreprocessedQuestion.class);
        } catch (Exception e) {
            return new PreprocessedQuestion(originalQuestion, List.of(originalQuestion));
        }
    }
}