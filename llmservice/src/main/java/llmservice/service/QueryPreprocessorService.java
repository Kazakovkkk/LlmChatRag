package llmservice.service;

import llmservice.dto.MessageDto;
import llmservice.dto.PreprocessedQuestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class QueryPreprocessorService {

    private static final String PREPROCESSED_SYSTEM_PROMPT = """
            Ты — главный диспетчер, лингвистический препроцессор и специалист по семантической оптимизации запросов для RAG-системы отеля.
            Твоя задача — проанализировать ТЕКУЩИЙ ВОПРОС пользователя (с учетом ИСТОРИИ БЕСЕДЫ) и вернуть строго структурированный JSON.
            
            ШАГ 1: ОПРЕДЕЛЕНИЕ НАМЕРЕНИЯ (intentType)
            1. "ACTION" — если пользователь выражает явное желание совершить действие, сделать заказ, вызвать персонал или запустить транзакцию.
               Примеры actionName:
               - ORDER_FOOD (заказ еды/напитков: "хочу пиццу", "принесите колу")
               - ROOM_CLEANING (уборка, вынос мусора, чистые полотенца: "приберитесь в номере")
               - HOTEL_CHECK_IN (вопросы заселения/выезда: "заселите меня в 3 номер")
            2. "SEARCH" — если это стандартный информационный вопрос об услугах отеля, правилах, времени работы объектов и т.д. ("до скольки работает бассейн?", "есть ли у вас парковка?").
            
            ШАГ 2: ПРАВИЛА ОБРАБОТКИ В ЗАВИСИМОСТИ ОТ ИНТЕНТА
            
            ЕСЛИ intentType == "ACTION":
            - Заполни поле "actionName" соответствующим идентификатором.
            - В поле "parameters" извлеки ключевые сущности в формате "ключ":"значение" (например: {"dish": "пицца", "drink": "кола"}).
            - Поля "normalized" и "alternatives" сделай null или пустыми.
            
            ЕСЛИ intentType == "SEARCH" (КРИТИЧЕСКИ ВАЖНО ДЛЯ RAG):
            - Поля "actionName" и "parameters" сделай null или пустыми.
            - Сформируй поле "normalized" и список "alternatives" (3 альтернативные формулировки на основе истории общения с пользователем), строго следуя правилам:
              1) Нормализация и очистка: Удали спецсимволы (оставь только !?.,), убери лишние пробелы, удали ЛЮБЫЕ смайлики/эмодзи. Расшифруй сокращения: «н-р» → «например», «т.д.» → «и так далее».
              2) Семантическое уплотнение: Сохрани ключевые термины, числа, имена собственные. Удали стоп-слова («очень», «просто», «ну») и вводные фразы. Заменяй местоимения на конкретные референсы из истории беседы (например, «он» → «бассейн», «во сколько он открывается?» → «во сколько открывается бассейн?»).
              3) ПЕРЕВОД ЗАПРОСА: Если вопрос задан не на русском языке, ОБЯЗАТЕЛЬНО переведи "normalized" и все "alternatives" на РУССКИЙ язык, так как векторная база данных отеля составлена на русском.
            
            ФОРМАТ ВЫВОДА:
            Вывод ТОЛЬКО в виде чистого JSON-объекта, без преамбул, комментариев и markdown-разметки (без ```json). Структура:
            {
              "intentType": "SEARCH" или "ACTION",
              "actionName": "СТРОКА",
              "parameters": { "ключ": "значение" },
              "normalized": "СТРОКА",
              "alternatives": ["строка1", "строка2", "строка3"]
            }
            """;
    private static final String PREPROCESSED_SYSTEM_PROMPT_test = """
            Ты — главный диспетчер, лингвистический препроцессор и специалист по семантической оптимизации запросов для RAG-системы отеля.
            Твоя задача — проанализировать ТЕКУЩИЙ ВОПРОС пользователя (с учетом ИСТОРИИ БЕСЕДЫ) и вернуть строго структурированный JSON.
            - Поля "actionName" и "parameters" сделай null или пустыми.
            - Сформируй поле "normalized" и список "alternatives" (3 альтернативные формулировки на основе истории общения с пользователем), строго следуя правилам:
              1) Нормализация и очистка: Удали спецсимволы (оставь только !?.,), убери лишние пробелы, удали ЛЮБЫЕ смайлики/эмодзи. Расшифруй сокращения: «н-р» → «например», «т.д.» → «и так далее».
              2) Семантическое уплотнение: Сохрани ключевые термины, числа, имена собственные. Удали стоп-слова («очень», «просто», «ну») и вводные фразы. Заменяй местоимения на конкретные референсы из истории беседы (например, «он» → «бассейн», «во сколько он открывается?» → «во сколько открывается бассейн?»).
              3) ПЕРЕВОД ЗАПРОСА: Если вопрос задан не на русском языке, ОБЯЗАТЕЛЬНО переведи "normalized" и все "alternatives" на РУССКИЙ язык, так как векторная база данных отеля составлена на русском.
            ФОРМАТ ВЫВОДА:
            Вывод ТОЛЬКО в виде чистого JSON-объекта, без преамбул, комментариев и markdown-разметки (без ```json). Структура:
            {
              "intentType": "SEARCH" или "ACTION",
              "actionName": "СТРОКА",
              "parameters": { "ключ": "значение" },
              "normalized": "СТРОКА",
              "alternatives": ["строка1", "строка2", "строка3"]
            }
            """;

    private final LlmRouter llmRouter;
    private final ObjectMapper objectMapper;
    public QueryPreprocessorService(LlmRouter llmRouter, ObjectMapper objectMapper) {
        this.llmRouter = llmRouter;
        this.objectMapper = objectMapper;
    }

    public Mono<PreprocessedQuestion> preprocessQuestion(
            String question,
            List<MessageDto> history
    ) {
        String historyContext = "";

        if (history != null && !history.isEmpty()) {
            historyContext = "История беседы:\n"
                    + history.stream()
                    .map(message ->
                            message.getRole()
                                    + ": "
                                    + message.getContent()
                    )
                    .collect(Collectors.joining("\n"))
                    + "\n\n";
        }

        String prompt = historyContext
                + PREPROCESSED_SYSTEM_PROMPT
                + "\n\nТекущий вопрос пользователя: "
                + question;

        return llmRouter.chat(prompt)
                .map(raw -> extractVariants(raw, question))
                .doOnNext(result ->
                        log.info(
                                "[llmservice] Результат препроцессинга: "
                                        + "intentType={}, actionName={}, "
                                        + "parameters={}, normalized={}, "
                                        + "alternatives={}",
                                result.getIntentType(),
                                result.getActionName(),
                                result.getParameters(),
                                result.getNormalized(),
                                result.getAlternatives()
                        )
                )
                .doOnError(error ->
                        log.error(
                                "[llmservice] Ошибка препроцессинга вопроса '{}'",
                                question,
                                error
                        )
                );
    }

    private PreprocessedQuestion extractVariants(String rawResponse, String originalQuestion) {
        try {
            String cleanJson = rawResponse.trim();
            if (cleanJson.startsWith("```json")) {
                cleanJson = cleanJson.substring(7);
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            }
            cleanJson = cleanJson.trim();

            //log.info("[llmservice] Сырой ответ LLM-классификатора: {}", cleanJson);
            return objectMapper.readValue(cleanJson, PreprocessedQuestion.class);
        } catch (Exception e) {
            log.error("Ошибка парсинга JSON классификации интентов. Fallback на SEARCH. Ошибка: {}", e.getMessage());
            // Безопасный Fallback на случай сбоя модели — отправляем в обычный поиск по базе знаний
            return new PreprocessedQuestion("SEARCH", null, Map.of(), originalQuestion, List.of(originalQuestion));
        }
    }
}