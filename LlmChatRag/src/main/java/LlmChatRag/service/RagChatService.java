package LlmChatRag.service;

import LlmChatRag.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagChatService {

    //private final RestClient qdrantRestClient;
    private final RestClient llmRestClient;
    private final RestClient adminRestClient;
    private final LlmStreamRouter llmStreamRouter;
    private final DocumentRankingService rankingService;
    private final SearchRouter searchRouter;// ← вместо ragGrpcClient
    private final ObjectMapper objectMapper;
    private final LlmPreprocessorRouter preprocessorRouter; // ← Вместо прямого RestClient llmRestClient для препроцессинга
    private final HotelActionRouter hotelActionRouter;     // ← Добавляем роутер действий

    public RagChatService(
            @Qualifier("llmRestClient") RestClient llmRestClient,
            @Qualifier("adminRestClient") RestClient adminRestClient,
            DocumentRankingService rankingService,
            SearchRouter searchRouter,
            LlmStreamRouter llmStreamRouter,
            LlmPreprocessorRouter preprocessorRouter, // Инжект
            HotelActionRouter hotelActionRouter,     // Инжект
            ObjectMapper objectMapper
    ) {
        this.llmRestClient = llmRestClient;
        this.adminRestClient = adminRestClient;
        this.rankingService = rankingService;
        this.searchRouter = searchRouter;
        this.llmStreamRouter = llmStreamRouter;
        this.preprocessorRouter = preprocessorRouter;
        this.hotelActionRouter = hotelActionRouter;
        this.objectMapper = objectMapper;
    }

    public String chat(String userQuestion, String hotelKey, List<MessageDto> history) {
        try {
            // Шаг 1: Препроцессинг вопроса → llm-service
            PreprocessedQuestion processed = preprocessQuestion(userQuestion, history);
            log.info("Preprocessed question: {}", processed.getNormalized());

            // Шаг 2: Поиск документов → qdrant-service
            List<DocumentDto> documents = searchDocuments(hotelKey, processed);
            log.info("Found {} documents", documents.size());

            // Шаг 3: Ранжирование
            List<DocumentDto> topDocs = rankingService.getTopK(documents, 5);

            // Шаг 4: Формирование контекста
            String context = topDocs.stream()
                    .map(DocumentDto::getText)
                    .collect(Collectors.joining("\n\n"));

            if (context.isBlank() || topDocs.isEmpty()) {
                return "Информация по данному вопросу отсутствует в базе знаний.";
            }
            // Шаг 5: Генерация ответа → llm-service
            return generateAnswer(userQuestion, context);

        } catch (Exception e) {
            log.error("Ошибка в RAG pipeline: {}", e.getMessage());
            return "Извините, произошла ошибка: " + e.getMessage();
        }
    }

    private PreprocessedQuestion preprocessQuestion(String question,
                                                    List<MessageDto> history) {
        Map<String, Object> body = new HashMap<>();
        body.put("question", question);
        body.put("history", history);

        return llmRestClient.post()
                .uri("/llm/preprocess")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(PreprocessedQuestion.class);
    }

    public Flux<String> chatStream(String hotelKey, String chatId, String userQuestion, List<MessageDto> history, String timestamp) {
        long totalStart = System.currentTimeMillis();
        StringBuilder fullBotResponse = new StringBuilder();
        long[] timings = new long[4];

        try {
            long t1 = System.currentTimeMillis();
            // Сохраняем входящую реплику гостя в аудит-лог Postgres
            syncMessageToAdminStoreAsync(hotelKey, chatId, "user", userQuestion);

            // Очистка спецсимволов
            userQuestion = userQuestion.replaceAll("[\\p{So}\\p{Cn}]", "");

            // 1. ВЫЗОВ ОБНОВЛЕННОГО РОУТЕРА ПРЕПРОЦЕССИНГА (REST/gRPC классификатор)
            PreprocessedQuestion processed = preprocessorRouter.preprocess(userQuestion, history);
            timings[0] = System.currentTimeMillis() - t1;

            // =========================================================================
            // 🌟 ВЕТВЛЕНИЕ: ПАЙПЛАЙН ВЗАИМОДЕЙСТВИЯ (ACTION)
            // =========================================================================
            if ("ACTION".equalsIgnoreCase(processed.getIntentType())) {
                log.info("🚀 [ПАЙПЛАЙН ДЕЙСТВИЙ] Обнаружено намерение транзакции: {}. Параметры: {}",
                        processed.getActionName(), processed.getParameters());

                // Формируем запрос к интеграционному микросервису отеля
                ActionRequest actionReq = new ActionRequest(hotelKey, chatId, processed.getActionName(), processed.getParameters());

                // Вызываем HotelActionService через роутер (REST или gRPC)
                ActionResponse actionRes = hotelActionRouter.execute(actionReq);

                String actionBotMessage = actionRes.getMessage();

                // Синхронизируем ответ отеля в базу данных аудита админки
                syncMessageToAdminStoreAsync(hotelKey, chatId, "assistant", actionBotMessage);

                log.info("🏆 [ПАЙПЛАЙН ДЕЙСТВИЙ] Выполнен за {} мс", System.currentTimeMillis() - totalStart);

                // Фронтенд чата ожидает токен в JSON, отдаем результат одним реактивным импульсом
                String jsonToken = objectMapper.writeValueAsString(Map.of("token", actionBotMessage));
                return Flux.just(jsonToken);
            }
            // =========================================================================


            // =========================================================================
            // 📄 СТАНДАРТНЫЙ ИНФОРМАЦИОННЫЙ ПАЙПЛАЙН (SEARCH / RAG)
            // =========================================================================
            log.info("📄 [ИНФОРМАЦИОННЫЙ ПАЙПЛАЙН] Вопрос: {} . Альтернативы: {}",
                    processed.getNormalized(), processed.getAlternatives());

            long t2 = System.currentTimeMillis();
            List<DocumentDto> documents = searchDocuments(hotelKey, processed);
            timings[1] = System.currentTimeMillis() - t2;

            long t3 = System.currentTimeMillis();
            List<DocumentDto> topDocs = rankingService.getTopK(documents, 5);
            timings[2] = System.currentTimeMillis() - t3;

            String context = topDocs.stream()
                    .map(DocumentDto::getText)
                    .collect(Collectors.joining("\n\n"));

            timings[3] = System.currentTimeMillis() - totalStart;

            if (context.isBlank() || topDocs.isEmpty()) {
                String fallbackMsg = "Информация по данному вопросу временно отсутствует в базе знаний отеля.";
                syncMessageToAdminStoreAsync(hotelKey, chatId, "assistant", fallbackMsg);
                return Flux.just("{\"token\":\"" + fallbackMsg + "\"}");
            }

            long streamStart = System.currentTimeMillis();

            return llmStreamRouter.stream(new AnswerRequest(userQuestion, context, history, timestamp))
                    .doOnNext(tokenMapJson -> {
                        try {
                            JsonNode node = objectMapper.readTree(tokenMapJson);
                            String token = node.path("token").asText("");
                            fullBotResponse.append(token);
                        } catch (Exception ignored) {}
                    })
                    .doOnComplete(() -> {
                        long streamTime = System.currentTimeMillis() - streamStart;
                        log.info("""
                        ╔══════════════════════════════════════╗
                        ║         ИТОГИ RAG PIPELINE           ║
                        ╠══════════════════════════════════════╣
                        ║ Препроцессинг:     {} мс
                        ║ Поиск ({}):      {} мс
                        ║ Ранжирование:      {} мс
                        ║ Подготовка итого:  {} мс
                        ║ Генерация (LLM/{}):   {} мс
                        ║ ПОЛНОЕ ВРЕМЯ:      {} мс
                        ╚══════════════════════════════════════╝
                        """,
                                timings[0],
                                searchRouter.getProtocol().toUpperCase(), timings[1],
                                timings[2], timings[3],
                                llmStreamRouter.getProtocol().toUpperCase(), streamTime,
                                System.currentTimeMillis() - totalStart);
                        syncMessageToAdminStoreAsync(hotelKey, chatId, "assistant", fullBotResponse.toString());
                    });

        } catch (Exception e) {
            log.error("Ошибка в объединенном RAG/Action pipeline: {}", e.getMessage());
            return Flux.just("{\"token\":\"Извините, произошла внутренняя ошибка системы.\"}");
        }
    }

    private List<DocumentDto> searchDocuments(String hotelKey, PreprocessedQuestion processed) {
        List<String> allQueries = new ArrayList<>();
        allQueries.add(processed.getNormalized());
        if (processed.getAlternatives() != null){
            allQueries.addAll(processed.getAlternatives());
        }
        List<DocumentDto> allDocuments = new ArrayList<>();
        for (String query : allQueries) {
            try {
                // ← теперь через роутер
                List<DocumentDto> docs = searchRouter.search(hotelKey, query, 5);
                if (docs != null && !docs.isEmpty()) {
                    log.info("=== Запрос: '{}' | Найдено документов: {} ===", query, docs.size());
                    docs.forEach(doc -> log.info(
                            "  📄 ID: {} | Score: {} | Text: {}",
                            doc.getId(),
                            doc.getScore(),
                            doc.getText()
                    ));
                    allDocuments.addAll(docs);
                } else {
                    log.warn("  ⚠️ Запрос '{}' вернул пустой результат", query);
                }

            } catch (Exception e) {
                log.warn("Ошибка поиска для запроса '{}': {}", query, e.getMessage());
            }
        }

        log.info("=== Итого документов до ранжирования: {} ===", allDocuments.size());
        return allDocuments;
    }

    private String generateAnswer(String question, String context) {
        return llmRestClient.post()
                .uri("/llm/answer")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AnswerRequest(question, context))
                .retrieve()
                .body(String.class);
    }
    private void syncMessageToAdminStoreAsync(String hotelKey, String chatId, String role, String content) {
        Flux.just(Map.of("hotelKey", hotelKey, "chatId", chatId, "role", role, "content", content))
                .subscribeOn(Schedulers.boundedElastic()) // Выполняем в фоне, чтобы не тормозить отдачу токенов гостю
                .subscribe(payload -> {
                    try {
                        adminRestClient.post()
                                .uri("/admin/chats/sync")
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(payload)
                                .retrieve()
                                .toBodilessEntity();
                        log.debug("♻️ Лог [{}] успешно синхронизирован с базой данных AdminPanel", role);
                    } catch (Exception e) {
                        log.error("⚠️ Не удалось отправить реплику в AdminPanel (база данных аудита недоступна): {}", e.getMessage());
                    }
                });
    }
    // Добавь этот метод в RagChatService.java
    public List<MessageDto> getChatHistoryFromAdmin(String hotelKey, String chatId) {
        try {
            // Делаем запрос к панели администратора для выгрузки логов конкретной сессии
            List<Map<String, String>> rawHistory = adminRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/admin/chats/history/single")
                            .queryParam("hotelKey", hotelKey)
                            .queryParam("chatId", chatId)
                            .build())
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {});

            if (rawHistory == null) return List.of();

            // Маппим сырые данные из БД в объекты MessageDto для фронтенда
            return rawHistory.stream().map(raw -> {
                MessageDto dto = new MessageDto();
                dto.setRole(raw.get("role"));
                dto.setContent(raw.get("content"));
                return dto;
            }).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("⚠️ Не удалось восстановить чат из AdminPanel: {}", e.getMessage());
            return List.of(); // Если админка или БД недоступны, возвращаем пустой список
        }
    }
}