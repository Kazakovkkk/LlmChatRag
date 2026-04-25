package LlmChatRag.service;

import LlmChatRag.dto.DocumentDto;
import LlmChatRag.dto.PreprocessedQuestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreprocessingEvaluationService {

    private final SearchRouter searchRouter;
    private final DocumentRankingService rankingService;
    private final RestClient llmRestClient;

    // Заглушка для твоего датасета. Замени на свой класс с тестовыми данными
    // private final EvaluationDataset dataset;

    public Map<String, Object> evaluatePreprocessingLift(int k) throws InterruptedException {
        // List<TestCase> testCases = dataset.getTestCases();

        List<TestCase> testCases = List.of(
                new TestCase("Где находит))ся))) фитнес и как он раfботает?", "Фитнес-центр отел . Время работы фитнес-центра с 7:00 до 23:00")
        );

        double totalMrrRaw = 0.0;
        double totalRecallRaw = 0.0;
        double totalMrrPrep = 0.0;
        double totalRecallPrep = 0.0;

        for (TestCase tc : testCases) {
            String expectedContext = tc.getExpectedContext();

            // === 1. ПОИСК БЕЗ ПРЕПРОЦЕССИНГА (RAW) ===
            List<DocumentDto> rawDocs = searchRouter.search(tc.getQuestion(), k);
            List<DocumentDto> rankedRawDocs = rankingService.getTopK(rawDocs, k);

            totalMrrRaw += calculateMRR(rankedRawDocs, expectedContext);
            totalRecallRaw += calculateRecall(rankedRawDocs, expectedContext);

            // === 2. ПОИСК С ПРЕПРОЦЕССИНГОМ (PROCESSED) ===
            PreprocessedQuestion prepQ = preprocessViaLlmService(tc.getQuestion());
            List<DocumentDto> prepDocs = searchDocumentsWithAlternatives(prepQ, k);
            List<DocumentDto> rankedPrepDocs = rankingService.getTopK(prepDocs, k);

            totalMrrPrep += calculateMRR(rankedPrepDocs, expectedContext);
            totalRecallPrep += calculateRecall(rankedPrepDocs, expectedContext);
            Thread.sleep(3000);
        }

        int size = testCases.size();

        // Формируем итоговые результаты
        Map<String, Object> rawMetrics = Map.of(
                "MRR", totalMrrRaw / size,
                "Recall", totalRecallRaw / size
        );

        Map<String, Object> prepMetrics = Map.of(
                "MRR", totalMrrPrep / size,
                "Recall", totalRecallPrep / size
        );

        Map<String, Object> liftMetrics = Map.of(
                "Delta_MRR", (totalMrrPrep / size) - (totalMrrRaw / size),
                "Delta_Recall", (totalRecallPrep / size) - (totalRecallRaw / size)
        );

        return Map.of(
                "test_cases_count", size,
                "metrics_RAW", rawMetrics,
                "metrics_PREPROCESSED", prepMetrics,
                "LIFT_EFFECTIVENESS", liftMetrics
        );
    }

    // Имитация твоего метода из RagChatService
    private PreprocessedQuestion preprocessViaLlmService(String question) {
        Map<String, Object> body = new HashMap<>();
        body.put("question", question);
        body.put("history", List.of());

        return llmRestClient.post()
                .uri("/llm/preprocess")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(PreprocessedQuestion.class);
    }

    // Сбор документов по нормализованному запросу и альтернативам (как у тебя в коде)
    private List<DocumentDto> searchDocumentsWithAlternatives(PreprocessedQuestion processed, int k) {
        List<String> allQueries = new ArrayList<>();
        allQueries.add(processed.getNormalized());
        log.info("Нормализованный вопрос: {}", processed.getNormalized());
        if (processed.getAlternatives() != null) {
            log.info("Альтернативный вопрос: {}", processed.getAlternatives());
            allQueries.addAll(processed.getAlternatives());
        }

        List<DocumentDto> allDocuments = new ArrayList<>();
        for (String query : allQueries) {
            List<DocumentDto> docs = searchRouter.search(query, k);
            if (docs != null) {
                allDocuments.addAll(docs);
            }
        }
        return allDocuments;
    }

    // --- Простейшие реализации метрик (замени на свои существующие, если они сложнее) ---
    private double calculateMRR(List<DocumentDto> docs, String expectedContext) {
        for (int i = 0; i < docs.size(); i++) {
            if (docs.get(i).getText().contains(expectedContext)) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    private double calculateRecall(List<DocumentDto> docs, String expectedContext) {
        boolean found = docs.stream().anyMatch(d -> d.getText().contains(expectedContext));
        return found ? 1.0 : 0.0;
    }

    // Вспомогательный класс для теста
    public static class TestCase {
        private String question;
        private String expectedContext;
        public TestCase(String question, String expectedContext) {
            this.question = question; this.expectedContext = expectedContext;
        }
        public String getQuestion() { return question; }
        public String getExpectedContext() { return expectedContext; }
    }
}