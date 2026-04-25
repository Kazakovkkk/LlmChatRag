package qdrantservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.*;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.JsonWithInt.Struct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class BM25StatsRepository {

    private static final String COLLECTION = "bm25_stats";
    private static final String STATS_POINT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String DF_POINT_ID    = "00000000-0000-0000-0000-000000000002";

    private final QdrantClient qdrantClient;
    private final ObjectMapper objectMapper;

    public BM25StatsRepository(QdrantClient qdrantClient) {
        this.qdrantClient = qdrantClient;
        this.objectMapper = new ObjectMapper();
    }

    // ─── Сохранение общей статистики ─────────────────────────
    public void saveGlobalStats(int totalDocuments,
                                double avgDocumentLength,
                                Map<String, Integer> documentLengths) {
        try {
            Map<String, Value> payload = new HashMap<>();
            payload.put("total_documents",
                    Value.newBuilder().setIntegerValue(totalDocuments).build());
            payload.put("avg_document_length",
                    Value.newBuilder().setDoubleValue(avgDocumentLength).build());
            payload.put("document_lengths_json",
                    Value.newBuilder()
                            .setStringValue(objectMapper.writeValueAsString(documentLengths))
                            .build());

            upsertPoint(STATS_POINT_ID, payload);
            log.debug("BM25 глобальная статистика сохранена | docs: {} | avgLen: {}",
                    totalDocuments, avgDocumentLength);

        } catch (Exception e) {
            log.error("Ошибка сохранения BM25 глобальной статистики: {}", e.getMessage());
        }
    }

    // ─── Сохранение document frequency ───────────────────────
    public void saveDocumentFrequency(Map<String, Integer> documentFrequency) {
        try {
            Map<String, Value> payload = new HashMap<>();
            payload.put("document_frequency_json",
                    Value.newBuilder()
                            .setStringValue(objectMapper.writeValueAsString(documentFrequency))
                            .build());

            upsertPoint(DF_POINT_ID, payload);
            log.debug("BM25 document frequency сохранён | {} терминов",
                    documentFrequency.size());

        } catch (Exception e) {
            log.error("Ошибка сохранения BM25 document frequency: {}", e.getMessage());
        }
    }

    // ─── Загрузка глобальной статистики ──────────────────────
    public Optional<GlobalStats> loadGlobalStats() {
        try {
            List<RetrievedPoint> points = qdrantClient.retrieveAsync(
                    COLLECTION,
                    List.of(PointId.newBuilder().setUuid(STATS_POINT_ID).build()),
                    true, false, null
            ).get();

            if (points.isEmpty()) return Optional.empty();

            Map<String, Value> payload = points.get(0).getPayload();

            int totalDocs = (int) payload.get("total_documents").getIntegerValue();
            double avgLen = payload.get("avg_document_length").getDoubleValue();
            String docLengthsJson = payload.get("document_lengths_json").getStringValue();

            Map<String, Integer> documentLengths = objectMapper.readValue(
                    docLengthsJson,
                    new TypeReference<Map<String, Integer>>() {}
            );

            return Optional.of(new GlobalStats(totalDocs, avgLen, documentLengths));

        } catch (Exception e) {
            log.warn("Не удалось загрузить BM25 глобальную статистику: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ─── Загрузка document frequency ─────────────────────────
    public Map<String, Integer> loadDocumentFrequency() {
        try {
            List<RetrievedPoint> points = qdrantClient.retrieveAsync(
                    COLLECTION,
                    List.of(PointId.newBuilder().setUuid(DF_POINT_ID).build()),
                    true, false, null
            ).get();

            if (points.isEmpty()) return new ConcurrentHashMap<>();

            String json = points.get(0).getPayload()
                    .get("document_frequency_json").getStringValue();

            return new ConcurrentHashMap<>(objectMapper.readValue(
                    json, new TypeReference<Map<String, Integer>>() {}
            ));

        } catch (Exception e) {
            log.warn("Не удалось загрузить BM25 document frequency: {}", e.getMessage());
            return new ConcurrentHashMap<>();
        }
    }

    // ─── Вспомогательный метод upsert ────────────────────────
    private void upsertPoint(String uuid, Map<String, Value> payload) throws Exception {
        qdrantClient.upsertAsync(
                COLLECTION,
                List.of(PointStruct.newBuilder()
                        .setId(PointId.newBuilder().setUuid(uuid).build())
                        .setVectors(Vectors.newBuilder()
                                .setVector(io.qdrant.client.grpc.Points.Vector.newBuilder()
                                        .addAllData(List.of(1.0f))
                                        .build())
                                .build())
                        .putAllPayload(payload)
                        .build())
        ).get();
    }

    // ─── DTO для глобальной статистики ───────────────────────
    public record GlobalStats(
            int totalDocuments,
            double avgDocumentLength,
            Map<String, Integer> documentLengths
    ) {}
}