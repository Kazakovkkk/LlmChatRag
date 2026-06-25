package qdrantservice.service;

import qdrantservice.model.RemoteEmbeddingModel;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.*;
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

    public HybridSearchService(QdrantClient qdrantClient,
                               RemoteEmbeddingModel embeddingService,
                               BM25Tokenizer tokenizer) {
        this.qdrantClient = qdrantClient;
        this.embeddingService = embeddingService;
        this.tokenizer = tokenizer;
    }


    public List<ScoredDocument> searchByVector(String collectionName, String query, int limit, float threshold) {
        float[] vector = embeddingService.embedQuery(query);

        List<Float> vectorList = new ArrayList<>();
        for (float v : vector) vectorList.add(v);

        try {
            List<ScoredPoint> points = qdrantClient.searchAsync(
                    SearchPoints.newBuilder()
                            .setCollectionName(collectionName)
                            .addAllVector(vectorList)
                            .setLimit(limit)
                            .setScoreThreshold(threshold)
                            .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                            .build()
            ).get();

            List<ScoredDocument> results = points.stream()
                    .map(p -> new ScoredDocument(p.getId().getUuid(), extractText(p), p.getScore(), "vector"))
                    .collect(Collectors.toList());

            /*--- ДОБАВЛЕНЫ ЛОГИ ДЛЯ СТАТИСТИКИ ВЕКТОРНОГО ПОИСКА ---
            log.info("[ВЕКТОРНЫЙ ПОИСК] Коллекция: '{}' | Найдено чанков: {}", collectionName, results.size());
            results.forEach(doc -> log.info("  ├── ID: {} | Vector Score: {} | Текст: '{}'",
                    doc.getId(),
                    String.format("%.4f", doc.getScore()),
                    doc.getText().replace("\n", " "))); // убираем переносы для красоты лога
            */
            return results;
        } catch (Exception e) {
            log.error("Ошибка векторного поиска в {}: {}", collectionName, e.getMessage());
            return List.of();
        }
    }


    public List<ScoredDocument> searchByKeyword(String mainCollection, String statsCollection, String query, int limit) {
        Map<Integer, Float> sparseVector = tokenizer.tokenizeWithBM25(statsCollection, query, null, false);

        if (sparseVector.isEmpty()) {
            log.warn("[КЛЮЧЕВОЙ ПОИСК] Токены BM25 пусты для запроса '{}'", query);
            return List.of();
        }

        List<Integer> indices = new ArrayList<>(sparseVector.keySet());
        List<Float> values = indices.stream().map(sparseVector::get).collect(Collectors.toList());

        try {
            List<ScoredPoint> points = qdrantClient.searchAsync(
                    SearchPoints.newBuilder()
                            .setCollectionName(mainCollection)
                            .setVectorName("sparse")
                            .addAllVector(values)
                            .setSparseIndices(SparseIndices.newBuilder().addAllData(indices).build())
                            .setLimit(limit)
                            .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                            .build()
            ).get();

            List<ScoredDocument> results = points.stream()
                    .map(p -> new ScoredDocument(p.getId().getUuid(), extractText(p), p.getScore(), "keyword"))
                    .collect(Collectors.toList());
            /*
            // --- ДОБАВЛЕНЫ ЛОГИ ДЛЯ СТАТИСТИКИ КЛЮЧЕВОГО ПОИСКА ---
            log.info("[КЛЮЧЕВОЙ ПОИСК (BM25)] Коллекция: '{}' | Найдено чанков: {}", mainCollection, results.size());
            results.forEach(doc -> log.info("  ├──  ID: {} | BM25 Score: {} | Текст: '{}'",
                    doc.getId(),
                    String.format("%.4f", doc.getScore()),
                    doc.getText().replace("\n", " ")));
            */
            return results;
        } catch (Exception e) {
            log.error("Ошибка ключевого поиска в {}: {}", mainCollection, e.getMessage());
            return List.of();
        }
    }


    public List<ScoredDocument> searchHybrid(String mainColl, String statsColl, String query, int limit, float threshold) {
        //log.info("Запуск гибридного конвейера RAG для запроса: '{}'", query);


        List<ScoredDocument> vectorResults = searchByVector(mainColl, query, limit * 2, threshold);
        List<ScoredDocument> keywordResults = searchByKeyword(mainColl, statsColl, query, limit * 2);

        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, String> idToTextMap = new HashMap<>();
        int k = 60;

        for (int i = 0; i < vectorResults.size(); i++) {
            ScoredDocument doc = vectorResults.get(i);
            rrfScores.merge(doc.getId(), 1.0 / (k + i + 1), Double::sum);
            idToTextMap.putIfAbsent(doc.getId(), doc.getText());
        }

        for (int i = 0; i < keywordResults.size(); i++) {
            ScoredDocument doc = keywordResults.get(i);
            rrfScores.merge(doc.getId(), 1.0 / (k + i + 1), Double::sum);
            idToTextMap.putIfAbsent(doc.getId(), doc.getText());
        }

        List<ScoredDocument> hybridResults = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new ScoredDocument(e.getKey(), idToTextMap.get(e.getKey()), e.getValue().floatValue(), "hybrid"))
                .collect(Collectors.toList());

        /*--- ДОБАВЛЕНЫ ЛОГИ ДЛЯ ИТОГОВОГО ГИБРИДНОГО РАНЖИРОВАНИЯ ---
        log.info("[ГИБРИДНОЕ СЛИЯНИЕ RRF] Финальный результат (Лимит: {}):", limit);
        if (hybridResults.isEmpty()) {
            log.warn("  Внимание: после слияния RRF список результатов пуст!");
        } else {
            for (int i = 0; i < hybridResults.size(); i++) {
                ScoredDocument doc = hybridResults.get(i);
                log.info("  ├── Позиция #{}: ID: {} | Итоговый RRF Score: {} | Текст: '{}'",
                        (i + 1),
                        doc.getId(),
                        String.format("%.6f", doc.getScore()),
                        doc.getText().replace("\n", " "));
            }
        }
        log.info("=========================================================================");
        */
        return hybridResults;
    }


    private String extractText(ScoredPoint point) {
        var payload = point.getPayload();

        if (payload.containsKey("doc_content")) {
            return payload.get("doc_content").getStringValue();
        }

        if (payload.containsKey("page_content")) {
            return payload.get("page_content").getStringValue();
        }
        if (payload.containsKey("content")) {
            return payload.get("content").getStringValue();
        }
        if (payload.containsKey("text")) {
            return payload.get("text").getStringValue();
        }

        return "";
    }
}