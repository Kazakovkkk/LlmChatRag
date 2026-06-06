package qdrantservice.service;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.*;
import io.qdrant.client.grpc.JsonWithInt.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.*;

@Slf4j
@Repository
@RequiredArgsConstructor
public class BM25StatsRepository {

    private final QdrantClient qdrantClient;

    // Глобальные константы ID для системных настроек внутри коллекции статистики тенанта
    private static final String GLOBAL_STATS_POINT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String DOC_LENGTHS_PREFIX_ID = "10000000-0000-0000-0000-";

    public void deleteCollectionStats(String statsCollection) {
        log.warn("🗑️ Запуск очистки репозитория BM25. Удаление всех статистических точек в коллекции Qdrant: {}", statsCollection);
        try {
            // Поскольку statsCollection уникальна для каждого отеля (например, hotel_stats_cosmos),
            // мы можем использовать пустой Filter, что заставит Qdrant очистить абсолютно все точки в ней.
            Filter emptyFilter = Filter.newBuilder().build();

            // Асинхронно отправляем команду удаления точек и блокируем поток через .get() до подтверждения
            qdrantClient.deleteAsync(statsCollection, emptyFilter).get();

            log.info("✅ Статистическая коллекция Qdrant [{}] полностью опустошена", statsCollection);
        } catch (Exception e) {
            log.error("❌ Ошибка при очистке статистических точек в Qdrant для {}: {}", statsCollection, e.getMessage());
            throw new RuntimeException("Не удалось сбросить персистентные матрицы BM25: " + e.getMessage(), e);
        }
    }

    public void saveGlobalStats(String statsCollection, int totalDocuments, double avgDocumentLength) {
        try {
            Map<String, Value> payload = new HashMap<>();
            payload.put("total_documents", Value.newBuilder().setIntegerValue(totalDocuments).build());
            payload.put("avg_document_length", Value.newBuilder().setDoubleValue(avgDocumentLength).build());
            payload.put("is_system_meta", Value.newBuilder().setBoolValue(true).build());

            upsertPoint(statsCollection, GLOBAL_STATS_POINT_ID, payload);
        } catch (Exception e) {
            log.error("Ошибка сохранения глобальной статистики в {}: {}", statsCollection, e.getMessage());
        }
    }

    public void saveTermFrequency(String statsCollection, String term, int frequency) {
        try {
            String pointUuid = UUID.nameUUIDFromBytes(term.getBytes()).toString();
            Map<String, Value> payload = new HashMap<>();
            payload.put("term", Value.newBuilder().setStringValue(term).build());
            payload.put("frequency", Value.newBuilder().setIntegerValue(frequency).build());
            payload.put("is_term", Value.newBuilder().setBoolValue(true).build());

            upsertPoint(statsCollection, pointUuid, payload);
        } catch (Exception e) {
            log.error("Ошибка сохранения частоты термина '{}' в {}: {}", term, statsCollection, e.getMessage());
        }
    }

    public void saveDocumentLength(String statsCollection, String documentId, int length) {
        try {
            // Генерируем UUID для длины документа на основе его собственного ID
            String pointUuid = UUID.nameUUIDFromBytes(("doclen_" + documentId).getBytes()).toString();
            Map<String, Value> payload = new HashMap<>();
            payload.put("doc_id", Value.newBuilder().setStringValue(documentId).build());
            payload.put("length", Value.newBuilder().setIntegerValue(length).build());
            payload.put("is_doc_length", Value.newBuilder().setBoolValue(true).build());

            upsertPoint(statsCollection, pointUuid, payload);
        } catch (Exception e) {
            log.error("Ошибка сохранения длины документа {} в {}: {}", documentId, statsCollection, e.getMessage());
        }
    }

    public Optional<GlobalStatsMeta> loadGlobalStats(String statsCollection) {
        try {
            List<RetrievedPoint> points = qdrantClient.retrieveAsync(
                    statsCollection,
                    List.of(PointId.newBuilder().setUuid(GLOBAL_STATS_POINT_ID).build()),
                    true, false, null
            ).get();

            if (points.isEmpty()) return Optional.empty();

            Map<String, Value> payload = points.get(0).getPayload();
            int totalDocs = (int) payload.get("total_documents").getIntegerValue();
            double avgLen = payload.get("avg_document_length").getDoubleValue();

            return Optional.of(new GlobalStatsMeta(totalDocs, avgLen));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public int loadTermFrequency(String statsCollection, String term) {
        try {
            String pointUuid = UUID.nameUUIDFromBytes(term.getBytes()).toString();
            List<RetrievedPoint> points = qdrantClient.retrieveAsync(
                    statsCollection,
                    List.of(PointId.newBuilder().setUuid(pointUuid).build()),
                    true, false, null
            ).get();

            if (points.isEmpty()) return 0;
            return (int) points.get(0).getPayload().get("frequency").getIntegerValue();
        } catch (Exception e) {
            return 0;
        }
    }

    public int loadDocumentLength(String statsCollection, String documentId) {
        try {
            String pointUuid = UUID.nameUUIDFromBytes(("doclen_" + documentId).getBytes()).toString();
            List<RetrievedPoint> points = qdrantClient.retrieveAsync(
                    statsCollection,
                    List.of(PointId.newBuilder().setUuid(pointUuid).build()),
                    true, false, null
            ).get();

            if (points.isEmpty()) return 0;
            return (int) points.get(0).getPayload().get("length").getIntegerValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private void upsertPoint(String collection, String uuid, Map<String, Value> payload) throws Exception {
        qdrantClient.upsertAsync(
                collection,
                List.of(PointStruct.newBuilder()
                        .setId(PointId.newBuilder().setUuid(uuid).build())
                        .setVectors(Vectors.newBuilder()
                                .setVector(Points.Vector.newBuilder().addAllData(List.of(1.0f)).build()) // Сервисный вектор-заглушка
                                .build())
                        .putAllPayload(payload)
                        .build())
        ).get();
    }

    public record GlobalStatsMeta(int totalDocuments, double avgDocumentLength) {}
}