package qdrantservice.service;

import qdrantservice.model.RemoteEmbeddingModel;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.*;
import io.qdrant.client.grpc.Points.SparseIndices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import qdrantservice.dto.ScoredDocument;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HybridSearchService {

    private final QdrantClient qdrantClient;
    private final RemoteEmbeddingModel embeddingService;
    private final BM25Tokenizer tokenizer;
    private final String collectionName;

    public HybridSearchService(
            QdrantClient qdrantClient,
            RemoteEmbeddingModel embeddingService,
            BM25Tokenizer tokenizer,
            @org.springframework.beans.factory.annotation.Value("${qdrant.collection-name:incidents}") String collectionName) {
        this.qdrantClient = qdrantClient;
        this.embeddingService = embeddingService;
        this.tokenizer = tokenizer;
        this.collectionName = collectionName;
    }

    public List<ScoredDocument> searchByVector(String query, int limit, float threshold) {
        long start = System.currentTimeMillis();

        float[] vector = embeddingService.embed(query);

        List<Float> vectorList = new ArrayList<>();
        for (float v : vector) vectorList.add(v);

        try {
            List<ScoredPoint> points = qdrantClient.searchAsync(
                    SearchPoints.newBuilder()
                            .setCollectionName(collectionName)
                            .addAllVector(vectorList)
                            .setLimit(limit)
                            .setScoreThreshold(threshold)
                            .setWithPayload(WithPayloadSelector.newBuilder()
                                    .setEnable(true).build())
                            .build()
            ).get();

            log.info("⏱ Векторный поиск: {} мс | найдено: {} документов",
                    System.currentTimeMillis() - start, points.size());

            return points.stream()
                    .map(p -> new ScoredDocument(
                            extractText(p),
                            p.getScore(),
                            "vector"
                    ))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Ошибка векторного поиска: {}", e.getMessage());
            return List.of();
        }
    }


    public List<ScoredDocument> searchByKeyword(String query, int limit) {
        long start = System.currentTimeMillis();

        Map<Integer, Float> sparseVector = tokenizer.tokenizeQuery(query);
        List<String> terms = tokenizer.tokenizeToTerms(query);
        log.info("BM25 запрос '{}' | Термины после стемминга: {}", query, terms);
        log.info("BM25 статистика | totalDocs: {} | avgDocLen: {}",
                tokenizer.getTotalDocuments(), tokenizer.getAvgDocumentLength());
        terms.forEach(term -> log.info(
                "  Термин '{}' | df: {} | IDF: {}",
                term,
                tokenizer.getDocumentFrequency().getOrDefault(term, 0),
                String.format("%.4f", Math.log(
                        (Math.max(tokenizer.getTotalDocuments(), 1)
                                - tokenizer.getDocumentFrequency().getOrDefault(term, 0) + 0.5)
                                / (tokenizer.getDocumentFrequency().getOrDefault(term, 0) + 0.5) + 1.0))
        ));
        log.info("BM25 sparse вектор запросу | {} ненулевых индексов", sparseVector.size());

        if (sparseVector.isEmpty()) {
            log.warn("BM25: пустой sparse вектор для запроса '{}'", query);
            return List.of();
        }

        log.info("BM25 запрос '{}' | {} уникальных терминов", query, sparseVector.size());

        List<Integer> indices = new ArrayList<>(sparseVector.keySet());
        List<Float> values = indices.stream()
                .map(sparseVector::get)
                .collect(Collectors.toList());

        try {
            List<ScoredPoint> points = qdrantClient.searchAsync(
                    SearchPoints.newBuilder()
                            .setCollectionName(collectionName)
                            .setVectorName("sparse")
                            .addAllVector(values)
                            .setSparseIndices(SparseIndices.newBuilder()
                                    .addAllData(indices)
                                    .build())
                            .setLimit(limit)
                            .setWithPayload(WithPayloadSelector.newBuilder()
                                    .setEnable(true).build())
                            .build()
            ).get();


            log.info("⏱ Ключевой поиск (BM25): {} мс | найдено: {} документов",
                    System.currentTimeMillis() - start, points.size());

            return points.stream()
                    .map(p -> new ScoredDocument(extractText(p), p.getScore(), "keyword"))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Ошибка ключевого поиска: {}", e.getMessage());
            return List.of();
        }
    }


    public List<ScoredDocument> searchHybrid(String query, int limit, float threshold) {
        long start = System.currentTimeMillis();

        List<ScoredDocument> vectorResults = searchByVector(query, limit * 2, threshold);
        List<ScoredDocument> keywordResults = searchByKeyword(query, limit * 2);


        Map<String, Float> vectorScoreMap = new HashMap<>();
        for (ScoredDocument doc : vectorResults) {
            vectorScoreMap.put(doc.getText(), doc.getScore());
        }

        Map<String, Float> keywordScoreMap = new HashMap<>();
        for (ScoredDocument doc : keywordResults) {
            keywordScoreMap.put(doc.getText(), doc.getScore());
        }


        Map<String, Double> rrfScores = new HashMap<>();
        int k = 60;

        for (int i = 0; i < vectorResults.size(); i++) {
            String text = vectorResults.get(i).getText();
            rrfScores.merge(text, 1.0 / (k + i + 1), Double::sum);
        }

        for (int i = 0; i < keywordResults.size(); i++) {
            String text = keywordResults.get(i).getText();
            rrfScores.merge(text, 1.0 / (k + i + 1), Double::sum);
        }

        List<ScoredDocument> hybridResults = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(e -> {
                    String text = e.getKey();
                    float rrfScore = e.getValue().floatValue();

                    ScoredDocument doc = new ScoredDocument(text, rrfScore, "hybrid");
                    doc.setVectorScore(vectorScoreMap.getOrDefault(text, null));
                    doc.setKeywordScore(keywordScoreMap.getOrDefault(text, null));
                    doc.setRrfScore(rrfScore);
                    return doc;
                })
                .collect(Collectors.toList());


        log.info("⏱ Гибридный поиск (RRF): {} мс | найдено: {} документов, Вопрос пользователя: {}",
                System.currentTimeMillis() - start, hybridResults.size(), query);

        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║           ДЕТАЛЬНЫЕ SCORE ГИБРИДНОГО ПОИСКА             ║");
        log.info("╠══════════════════════════════════════════════════════════╣");

        for (int i = 0; i < hybridResults.size(); i++) {
            ScoredDocument doc = hybridResults.get(i);
            String vectorStr = doc.getVectorScore() != null
                    ? String.format("%.4f", doc.getVectorScore())
                    : "—";
            String keywordStr = doc.getKeywordScore() != null
                    ? String.format("%.4f", doc.getKeywordScore())
                    : "—";

            log.info("║ #{} | Vector: {} | Keyword: {} | RRF: {}",
                    i + 1,
                    vectorStr,
                    keywordStr,
                    String.format("%.6f", doc.getRrfScore()));
            log.info("║     Текст: {}",
                    doc.getText().substring(0, Math.min(60, doc.getText().length())) + "...");
            log.info("╠══════════════════════════════════════════════════════════╣");
        }

        log.info("╚══════════════════════════════════════════════════════════╝");

        return hybridResults;
    }

    private String extractText(ScoredPoint point) {
        try {
            // Spring AI сохраняет текст в "page_content"
            if (point.getPayload().containsKey("page_content")) {
                return point.getPayload().get("page_content").getStringValue();
            }
            // Fallback на doc_content
            if (point.getPayload().containsKey("doc_content")) {
                return point.getPayload().get("doc_content").getStringValue();
            }
            return "";
        } catch (Exception e) {
            log.warn("Не удалось извлечь текст из точки: {}", e.getMessage());
            return "";
        }
    }
}