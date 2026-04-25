package LlmChatRag.service;

import LlmChatRag.dto.AnswerRequest;
import LlmChatRag.dto.DocumentDto;
import LlmChatRag.dto.MessageDto;
import LlmChatRag.dto.PreprocessedQuestion;
import LlmChatRag.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

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
    private final LlmStreamRouter llmStreamRouter;
    private final DocumentRankingService rankingService;
    private final SearchRouter searchRouter; // ← вместо ragGrpcClient

    public RagChatService(
            @Qualifier("llmRestClient") RestClient llmRestClient,
            DocumentRankingService rankingService,
            SearchRouter searchRouter,
            LlmStreamRouter llmStreamRouter
    ) {
        this.llmRestClient = llmRestClient;
        this.rankingService = rankingService;
        this.searchRouter = searchRouter;
        this.llmStreamRouter = llmStreamRouter;
    }

    public String chat(String userQuestion, List<MessageDto> history) {
        try {
            // Шаг 1: Препроцессинг вопроса → llm-service
            PreprocessedQuestion processed = preprocessQuestion(userQuestion, history);
            log.info("Preprocessed question: {}", processed.getNormalized());

            // Шаг 2: Поиск документов → qdrant-service
            List<DocumentDto> documents = searchDocuments(processed);
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

    public Flux<String> chatStream(String userQuestion, List<MessageDto> history, String timestamp) {
        long totalStart = System.currentTimeMillis();

        // Для хранения времени каждого шага
        long[] timings = new long[4]; // [преп, поиск, ранжирование, подготовка]

        try {
            long t1 = System.currentTimeMillis();
            userQuestion = userQuestion.replaceAll("[\\p{So}\\p{Cn}]", "");
            PreprocessedQuestion processed = new PreprocessedQuestion();
            processed =  preprocessQuestion(userQuestion, history);
            processed.setNormalized(userQuestion);
            log.info("Вопрос пользователя: {} . Нормализованный вопрос: {} Альтернативные вопросы: {}",
                    userQuestion, processed.getNormalized(), processed.getAlternatives());
            timings[0] = System.currentTimeMillis() - t1;

            long t2 = System.currentTimeMillis();
            List<DocumentDto> documents = searchDocuments(processed);
            timings[1] = System.currentTimeMillis() - t2;

            long t3 = System.currentTimeMillis();
            List<DocumentDto> topDocs = rankingService.getTopK(documents, 3);
            timings[2] = System.currentTimeMillis() - t3;

            String context = topDocs.stream()
                    .map(DocumentDto::getText)
                    .collect(Collectors.joining("\n\n"));

            timings[3] = System.currentTimeMillis() - totalStart;

            if (context.isBlank() || topDocs.isEmpty()) {
                log.info("""
                        ╔══════════════════════════════════════╗
                        ║         ИТОГИ RAG PIPELINE           ║
                        ╠══════════════════════════════════════╣
                        ║ Препроцессинг:     {} мс
                        ║ Поиск ({}):      {} мс
                        ║ Ранжирование:      {} мс
                        ║ Подготовка итого:  {} мс
                        ║ Генерация:         — (контекст пуст)
                        ╚══════════════════════════════════════╝
                        """,
                        timings[0], searchRouter.getProtocol().toUpperCase(), timings[1], timings[2], timings[3]);
                return Flux.just("{\"token\":\"Информация по данному вопросу отсутствует в базе знаний.\"}");
            }

            long streamStart = System.currentTimeMillis();

            return llmStreamRouter.stream(new AnswerRequest(userQuestion, context, history, timestamp))
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
                    });

        } catch (Exception e) {
            log.error("Ошибка в RAG pipeline: {}", e.getMessage());
            return Flux.just("{\"token\":\"Извините, произошла ошибка.\"}");
        }
    }

    private List<DocumentDto> searchDocuments(PreprocessedQuestion processed) {
        List<String> allQueries = new ArrayList<>();
        allQueries.add(processed.getNormalized());
        if (processed.getAlternatives() != null){
            allQueries.addAll(processed.getAlternatives());
        }


        List<DocumentDto> allDocuments = new ArrayList<>();
        for (String query : allQueries) {
            try {
                // ← теперь через роутер
                List<DocumentDto> docs = searchRouter.search(query, 5);

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
}