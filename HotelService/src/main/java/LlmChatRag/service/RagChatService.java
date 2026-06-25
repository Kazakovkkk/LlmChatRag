package LlmChatRag.service;

import LlmChatRag.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;


import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class RagChatService {

    private final RestClient llmRestClient;
    private final RestClient adminRestClient;
    private final LlmStreamRouter llmStreamRouter;
    private final DocumentRankingService rankingService;
    private final SearchRouter searchRouter;// ← вместо ragGrpcClient
    private final ObjectMapper objectMapper;
    private final LlmPreprocessorRouter preprocessorRouter;
    private final HotelActionRouter hotelActionRouter;

    public RagChatService(
            @Qualifier("llmRestClient") RestClient llmRestClient,
            @Qualifier("adminRestClient") RestClient adminRestClient,
            DocumentRankingService rankingService,
            SearchRouter searchRouter,
            LlmStreamRouter llmStreamRouter,
            LlmPreprocessorRouter preprocessorRouter,
            HotelActionRouter hotelActionRouter,
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
            PreprocessedQuestion processed = preprocessQuestion(userQuestion, history);
            //log.info("Preprocessed question: {}", processed.getNormalized());
            List<DocumentDto> documents = searchDocuments(hotelKey, processed);
            //log.info("Found {} documents", documents.size());
            List<DocumentDto> topDocs = rankingService.getTopK(documents, 5);
            String context = topDocs.stream()
                    .map(DocumentDto::getText)
                    .collect(Collectors.joining("\n\n"));
            if (context.isBlank() || topDocs.isEmpty()) {
                return "Информация по данному вопросу отсутствует в базе знаний.";
            }
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
            syncMessageToAdminStoreAsync(hotelKey, chatId, "user", userQuestion);
            userQuestion = userQuestion.replaceAll("[\\p{So}\\p{Cn}]", "");
            PreprocessedQuestion processed = preprocessorRouter.preprocess(userQuestion, history);
            timings[0] = System.currentTimeMillis() - t1;

            if ("ACTION".equalsIgnoreCase(processed.getIntentType())) {
                log.info("Обнаружено намерение транзакции: {}. Параметры: {}",
                        processed.getActionName(), processed.getParameters());

                ActionRequest actionReq = new ActionRequest(hotelKey, chatId, processed.getActionName(), processed.getParameters());

                ActionResponse actionRes = hotelActionRouter.execute(actionReq);

                String actionBotMessage = actionRes.getMessage();

                syncMessageToAdminStoreAsync(hotelKey, chatId, "assistant", actionBotMessage);

                log.info("Выполнен за {} мс", System.currentTimeMillis() - totalStart);

                String jsonToken = objectMapper.writeValueAsString(Map.of("token", actionBotMessage));
                return Flux.just(jsonToken);
            }


            log.info("Вопрос: {} . Альтернативы: {}",
                    processed.getNormalized(), processed.getAlternatives());

            long t2 = System.currentTimeMillis();
            List<DocumentDto> documents = searchDocuments(hotelKey, processed);
            timings[1] = System.currentTimeMillis() - t2;

            long t3 = System.currentTimeMillis();
            List<DocumentDto> topDocs = rankingService.getTopK(documents, 10);
            timings[2] = System.currentTimeMillis() - t3;

            String context = topDocs.stream()
                    .map(DocumentDto::getText)
                    .collect(Collectors.joining("\n\n"));

            timings[3] = System.currentTimeMillis() - totalStart;

            List<MessageDto> limitedHistory = history != null && history.size() > 6
                    ? history.subList(history.size() - 6, history.size())
                    : history;

            if (context.isBlank() || topDocs.isEmpty()) {
                String fallbackMsg = "Информация по данному вопросу временно отсутствует в базе знаний отеля. Сообщение передано персоналу.";
                syncMessageToAdminStoreAsync(hotelKey, chatId, "assistant", fallbackMsg);
                return Flux.just("{\"token\":\"" + fallbackMsg + "\"}");
            }

            long streamStart = System.currentTimeMillis();

            Flux<String> llmResponseFlux = llmStreamRouter.stream(new AnswerRequest(userQuestion, context, limitedHistory, timestamp))
                    .doOnNext(tokenMapJson -> {
                        try {
                            JsonNode node = objectMapper.readTree(tokenMapJson);
                            String token = node.path("token").asText("");
                            fullBotResponse.append(token);
                        } catch (Exception ignored) {}
                    });

            return llmResponseFlux.concatWith(Flux.defer(() -> {
                String finalResponse = fullBotResponse.toString();

                if (finalResponse.contains("Информации нет в базе данных")) {
                    String appendNotice = " Для уточнения сообщение передано персоналу отеля. Ждите ответа.";
                    fullBotResponse.append(appendNotice);
                    try {
                        return Flux.just(objectMapper.writeValueAsString(Map.of("token", appendNotice)));
                    } catch (Exception e) {
                        return Flux.just("{\"token\":\"" + appendNotice + "\"}");
                    }
                }
                return Flux.empty();
            })).doOnComplete(() -> {
                long streamTime = System.currentTimeMillis() - streamStart;
                log.info("""
            ╔══════════════════════════════════════╗
            ║         ИТОГИ RAG PIPELINE           ║
            ╠══════════════════════════════════════╣
            ║ Препроцессинг:     {} мс
            ║ Поиск:             {} мс
            ║ Ранжирование:      {} мс
            ║ Генерация (LLM):   {} мс
            ║ ПОЛНОЕ ВРЕМЯ:      {} мс
            ╚══════════════════════════════════════╝
            """, timings[0], timings[1], timings[2], streamTime, System.currentTimeMillis() - totalStart);

                syncMessageToAdminStoreAsync(hotelKey, chatId, "assistant", fullBotResponse.toString());
            });

        } catch (Exception e) {
            log.error("Ошибка в объединенном RAG/Action pipeline: {}", e.getMessage());
            return Flux.just("{\"token\":\"Извините, произошла внутренняя ошибка системы.\"}");
        }
    }

    private List<DocumentDto> searchDocuments(
            String hotelKey,
            PreprocessedQuestion processed
    ) {
        List<String> queries = Stream.concat(
                        Stream.of(processed.getNormalized()),
                        processed.getAlternatives() == null
                                ? Stream.empty()
                                : processed.getAlternatives().stream()
                )
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(q -> !q.isBlank())
                .distinct()
                .limit(4)
                .toList();

        log.info("Batch search queries: {}", queries);

        return searchRouter.searchBatch(hotelKey, queries, 5);
    }
    public String chat_test(String userQuestion, String hotelKey, List<MessageDto> history) {
        try {
            PreprocessedQuestion processed = preprocessQuestion(userQuestion, history);
            //log.info("Preprocessed question: {}", processed.getNormalized());
            List<String> allQueries = new ArrayList<>();
            StringBuilder context = new StringBuilder();
            allQueries.add(processed.getNormalized());
            if (processed.getAlternatives() != null) {
                allQueries.addAll(processed.getAlternatives());
                for (String allQuery : allQueries) {
                    context.append(allQuery);
                }
            }

            return generateAnswer(userQuestion, context.toString());

        } catch (Exception e) {
            log.error("Ошибка в RAG pipeline: {}", e.getMessage());
            return "Извините, произошла ошибка: " + e.getMessage();
        }
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
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(payload -> {
                    try {
                        adminRestClient.post()
                                .uri("/admin/chats/sync")
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(payload)
                                .retrieve()
                                .toBodilessEntity();

                    } catch (Exception e) {
                        log.error("Не удалось отправить реплику в AdminPanel (база данных аудита недоступна): {}", e.getMessage());
                    }
                });
    }
    public List<MessageDto> getChatHistoryFromAdmin(String hotelKey, String chatId) {
        try {

            List<Map<String, String>> rawHistory = adminRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/admin/chats/history/single")
                            .queryParam("hotelKey", hotelKey)
                            .queryParam("chatId", chatId)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (rawHistory == null) return List.of();
            return rawHistory.stream().map(raw -> {
                MessageDto dto = new MessageDto();
                dto.setRole(raw.get("role"));
                dto.setContent(raw.get("content"));
                return dto;
            }).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Не удалось восстановить чат из AdminPanel: {}", e.getMessage());
            return List.of();
        }
    }
}