package qdrantservice.metrics;

import qdrantservice.service.IncidentEmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RetrievalMetricsService {

    private final IncidentEmbeddingService embeddingService;
    private final EvaluationDataset dataset;

    public RetrievalMetricsService(IncidentEmbeddingService embeddingService,
                                   EvaluationDataset dataset) {
        this.embeddingService = embeddingService;
        this.dataset = dataset;
    }

    // ─── Главный метод оценки ─────────────────────────────────
    public Map<String, Object> evaluate(String searchType, int k) throws InterruptedException {
        List<EvaluationDataset.TestCase> testCases = dataset.getTestCases();

        List<Double> precisions = new ArrayList<>();
        List<Double> recalls = new ArrayList<>();
        List<Double> reciprocalRanks = new ArrayList<>();
        List<Double> ndcgs = new ArrayList<>();
        List<Long> searchTimesMs = new ArrayList<>();

        List<Map<String, Object>> details = new ArrayList<>();

        for (EvaluationDataset.TestCase testCase : testCases) {
            long searchStart = System.currentTimeMillis();

            List<Document> retrieved = embeddingService
                    .searchSimilarIncidents(testCase.question(), k, searchType);

            long searchTimeMs = System.currentTimeMillis() - searchStart;
            searchTimesMs.add(searchTimeMs);

            // Определяем релевантность каждого документа
            List<Boolean> relevanceList = retrieved.stream()
                    .map(doc -> isRelevant(doc, testCase))
                    .collect(Collectors.toList());

            double precision = calculatePrecisionAtK(relevanceList, k);
            double recall = calculateRecallAtK(retrieved, testCase, k);
            double mrr = calculateReciprocalRank(relevanceList);
            double ndcg = calculateNDCG(relevanceList, k);

            precisions.add(precision);
            recalls.add(recall);
            reciprocalRanks.add(mrr);
            ndcgs.add(ndcg);

            // Детали по каждому вопросу
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("question", testCase.question());
            detail.put("precision@" + k, String.format("%.3f", precision));
            detail.put("recall@" + k, String.format("%.3f", recall));
            detail.put("mrr", String.format("%.3f", mrr));
            detail.put("ndcg@" + k, String.format("%.3f", ndcg));
            detail.put("retrieved_count", retrieved.size());
            detail.put("relevant_found", relevanceList.stream().filter(b -> b).count());
            detail.put("searchTimeMs", searchTimeMs);
            details.add(detail);
        }

        // Итоговые метрики
        double avgPrecision = precisions.stream().mapToDouble(d -> d).average().orElse(0);
        double avgRecall = recalls.stream().mapToDouble(d -> d).average().orElse(0);
        double meanMRR = reciprocalRanks.stream().mapToDouble(d -> d).average().orElse(0);
        double avgNDCG = ndcgs.stream().mapToDouble(d -> d).average().orElse(0);

        double avgSearchTimeMs = searchTimesMs.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);

        long minSearchTimeMs = searchTimesMs.stream()
                .mapToLong(Long::longValue)
                .min()
                .orElse(0L);

        long maxSearchTimeMs = searchTimesMs.stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);

        long totalSearchTimeMs = searchTimesMs.stream()
                .mapToLong(Long::longValue)
                .sum();

        // Логируем итоги
        logResults(searchType, k, testCases.size(), avgPrecision, avgRecall, meanMRR, avgNDCG);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("searchType", searchType);
        result.put("k", k);
        result.put("totalQuestions", testCases.size());
        result.put("metrics", Map.of(
                "precision@K", String.format("%.4f", avgPrecision),
                "recall@K", String.format("%.4f", avgRecall),
                "MRR", String.format("%.4f", meanMRR),
                "NDCG@K", String.format("%.4f", avgNDCG)
        ));
        result.put("searchTime", Map.of(
                "avgSearchTimeMs", String.format("%.2f", avgSearchTimeMs),
                "minSearchTimeMs", minSearchTimeMs,
                "maxSearchTimeMs", maxSearchTimeMs,
                "totalSearchTimeMs", totalSearchTimeMs
        ));
        result.put("details", details);

        return result;
    }

    //сколько из k найденных документов релевантны
    // Precision@K показывает, какая доля найденных документов среди первых K является релевантной.
    private double calculatePrecisionAtK(List<Boolean> relevance, int k) {
        int denominator = Math.min(k, relevance.size());

        if (denominator == 0) {
            return 0.0;
        }

        long relevant = relevance.stream()
                .limit(k)
                .filter(Boolean::booleanValue)
                .count();

        return (double) relevant / denominator;
    }

    // Recall@K показывает, какая доля ключевой информации из эталонного ответа
// была найдена в первых K возвращённых чанках.
    private double calculateRecallAtK(List<Document> retrieved,
                                      EvaluationDataset.TestCase testCase,
                                      int k) {
        List<String> keywords = testCase.relevantKeywords();

        if (keywords == null || keywords.isEmpty()) {
            return 0.0;
        }

        String combinedTopKText = retrieved.stream()
                .limit(k)
                .map(Document::getText)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.joining(" "));

        long foundKeywords = keywords.stream()
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .filter(combinedTopKText::contains)
                .count();

        return (double) foundKeywords / keywords.size();
    }

    // MRR (Mean Reciprocal Rank) Как быстро пользователь наткнется на первый правильный ответ?»
    // Как считается: Берется место (ранг) первого релевантного документа.

    private double calculateReciprocalRank(List<Boolean> relevance) {
        for (int i = 0; i < relevance.size(); i++) {
            if (relevance.get(i)) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    //Вопрос: «Насколько хорошо отсортированы результаты?»
    //
    //Как считается: Она похожа на Precision, но с «штрафом» за позицию.
    // Чем ниже находится полезный документ, тем меньше «очков» он приносит системе. При этом итоговый результат сравнивается с «идеальным» порядком.
    private double calculateNDCG(List<Boolean> relevance, int k) {
        double dcg = 0.0;
        for (int i = 0; i < Math.min(k, relevance.size()); i++) {
            if (relevance.get(i)) {
                dcg += 1.0 / (Math.log(i + 2) / Math.log(2));
            }
        }

        // Ideal DCG — все релевантные документы на первых позициях
        long relevantCount = relevance.stream().filter(b -> b).count();
        double idcg = 0.0;
        for (int i = 0; i < Math.min(k, relevantCount); i++) {
            idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        }

        return idcg == 0 ? 0.0 : dcg / idcg;
    }

    // ─── Проверка релевантности документа ────────────────────
    private boolean isRelevant(Document doc, EvaluationDataset.TestCase testCase) {
        String text = doc.getText().toLowerCase();
        List<String> keywords = testCase.relevantKeywords();

        // Документ релевантен если содержит хотя бы половину ключевых слов
        long matchCount = keywords.stream()
                .filter(keyword -> text.contains(keyword.toLowerCase()))
                .count();

        return matchCount >= Math.ceil(keywords.size() / 2.0);
    }

    // ─── Логирование результатов ──────────────────────────────
    private void logResults(String searchType, int k, int questionsCount,
                            double precision, double recall,
                            double mrr, double ndcg) {
        log.info("""
    ╔══════════════════════════════════════════════════════╗
    ║         МЕТРИКИ КАЧЕСТВА ПОИСКА                      ║
    ╠══════════════════════════════════════════════════════╣
    ║ Тип поиска:    {}
    ║ K:             {}
    ║ Вопросов:      {}
    ╠══════════════════════════════════════════════════════╣
    ║ Precision@K:   {}
    ║ Recall@K:      {}
    ║ MRR:           {}
    ║ NDCG@K:        {}
    ╚══════════════════════════════════════════════════════╝
    """,
                searchType, k, questionsCount,
                String.format("%.4f", precision),
                String.format("%.4f", recall),
                String.format("%.4f", mrr),
                String.format("%.4f", ndcg));
    }
}