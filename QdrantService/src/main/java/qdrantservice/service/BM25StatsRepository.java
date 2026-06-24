package qdrantservice.service;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.PointId;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import io.qdrant.client.grpc.Points.Vector;
import io.qdrant.client.grpc.Points.Vectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class BM25StatsRepository {

    private static final String GLOBAL_STATS_POINT_ID =
            "00000000-0000-0000-0000-000000000001";

    private static final String TERMS_KEY = "terms";
    private static final long CACHE_TTL_MS = 30_000;

    private final QdrantClient qdrantClient;

    private final Map<String, CachedGlobalStats> globalStatsCache =
            new ConcurrentHashMap<>();

    private record CachedGlobalStats(
            GlobalStatsMeta meta,
            long expiresAt
    ) {
    }

    public record GlobalStatsMeta(
            int totalDocuments,
            double avgDocumentLength
    ) {
    }

    public record DocumentStats(
            int length,
            Set<String> terms
    ) {
        public DocumentStats {
            terms = Set.copyOf(terms);
        }
    }

    public void deleteCollectionStats(String statsCollection) {
        log.warn(
                "Очистка BM25-статистики коллекции {}",
                statsCollection
        );

        try {
            Filter emptyFilter = Filter.newBuilder().build();

            qdrantClient.deleteAsync(
                    statsCollection,
                    emptyFilter
            ).get();

            log.info(
                    "BM25-статистика коллекции {} очищена",
                    statsCollection
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Не удалось очистить BM25-статистику коллекции "
                            + statsCollection,
                    e
            );
        } finally {
            invalidateGlobalStats(statsCollection);
        }
    }

    public Optional<GlobalStatsMeta> loadGlobalStats(
            String statsCollection
    ) {
        CachedGlobalStats cached =
                globalStatsCache.get(statsCollection);

        if (cached != null
                && System.currentTimeMillis() < cached.expiresAt()) {
            return Optional.of(cached.meta());
        }

        try {
            List<RetrievedPoint> points =
                    qdrantClient.retrieveAsync(
                            statsCollection,
                            List.of(
                                    PointId.newBuilder()
                                            .setUuid(GLOBAL_STATS_POINT_ID)
                                            .build()
                            ),
                            true,
                            false,
                            null
                    ).get();

            if (points.isEmpty()) {
                return Optional.empty();
            }

            Map<String, Value> payload =
                    points.getFirst().getPayloadMap();

            Value totalDocumentsValue =
                    payload.get("total_documents");

            Value avgDocumentLengthValue =
                    payload.get("avg_document_length");

            if (totalDocumentsValue == null
                    || avgDocumentLengthValue == null) {
                return Optional.empty();
            }

            GlobalStatsMeta meta = new GlobalStatsMeta(
                    (int) totalDocumentsValue.getIntegerValue(),
                    avgDocumentLengthValue.getDoubleValue()
            );

            cacheGlobalStats(statsCollection, meta);

            return Optional.of(meta);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Не удалось загрузить глобальную BM25-статистику "
                            + statsCollection,
                    e
            );
        }
    }

    public Optional<DocumentStats> loadDocumentStats(
            String statsCollection,
            String documentId
    ) {
        try {
            String pointId = documentStatsPointId(documentId);

            List<RetrievedPoint> points =
                    qdrantClient.retrieveAsync(
                            statsCollection,
                            List.of(
                                    PointId.newBuilder()
                                            .setUuid(pointId)
                                            .build()
                            ),
                            true,
                            false,
                            null
                    ).get();

            if (points.isEmpty()) {
                return Optional.empty();
            }

            Map<String, Value> payload =
                    points.getFirst().getPayloadMap();

            Value lengthValue = payload.get("length");
            int length = lengthValue == null
                    ? 0
                    : (int) lengthValue.getIntegerValue();

            Value termsValue = payload.get(TERMS_KEY);
            String encodedTerms = termsValue == null
                    ? ""
                    : termsValue.getStringValue();

            Set<String> terms;

            if (encodedTerms.isBlank()) {
                terms = Set.of();
            } else {
                terms = Arrays.stream(encodedTerms.split(" "))
                        .filter(term -> !term.isBlank())
                        .collect(Collectors.toUnmodifiableSet());
            }

            return Optional.of(
                    new DocumentStats(length, terms)
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Не удалось загрузить BM25-статистику документа "
                            + documentId,
                    e
            );
        }
    }

    public Map<String, Integer> loadTermFrequenciesBatch(
            String statsCollection,
            Set<String> terms
    ) {
        if (terms.isEmpty()) {
            return Map.of();
        }

        List<PointId> ids = terms.stream()
                .map(this::termPointId)
                .map(uuid -> PointId.newBuilder()
                        .setUuid(uuid)
                        .build())
                .toList();

        try {
            List<RetrievedPoint> points =
                    qdrantClient.retrieveAsync(
                            statsCollection,
                            ids,
                            true,
                            false,
                            null
                    ).get();

            Map<String, Integer> frequencies =
                    new HashMap<>();

            for (RetrievedPoint point : points) {
                Map<String, Value> payload =
                        point.getPayloadMap();

                Value termValue = payload.get("term");
                Value frequencyValue =
                        payload.get("frequency");

                if (termValue == null
                        || frequencyValue == null) {
                    continue;
                }

                frequencies.put(
                        termValue.getStringValue(),
                        (int) frequencyValue.getIntegerValue()
                );
            }

            return frequencies;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Не удалось загрузить частоты BM25-терминов",
                    e
            );
        }
    }

    public PointStruct buildGlobalStatsPoint(
            int totalDocuments,
            double avgDocumentLength
    ) {
        Map<String, Value> payload = new HashMap<>();

        payload.put(
                "total_documents",
                Value.newBuilder()
                        .setIntegerValue(totalDocuments)
                        .build()
        );

        payload.put(
                "avg_document_length",
                Value.newBuilder()
                        .setDoubleValue(avgDocumentLength)
                        .build()
        );

        payload.put(
                "is_system_meta",
                Value.newBuilder()
                        .setBoolValue(true)
                        .build()
        );

        return buildServicePoint(
                GLOBAL_STATS_POINT_ID,
                payload
        );
    }

    public PointStruct buildTermFrequencyPoint(
            String term,
            int frequency
    ) {
        Map<String, Value> payload = new HashMap<>();

        payload.put(
                "term",
                Value.newBuilder()
                        .setStringValue(term)
                        .build()
        );

        payload.put(
                "frequency",
                Value.newBuilder()
                        .setIntegerValue(frequency)
                        .build()
        );

        payload.put(
                "is_term",
                Value.newBuilder()
                        .setBoolValue(true)
                        .build()
        );

        return buildServicePoint(
                termPointId(term),
                payload
        );
    }

    public PointStruct buildDocumentStatsPoint(
            String documentId,
            int length,
            Set<String> terms
    ) {
        String encodedTerms = terms.stream()
                .sorted()
                .collect(Collectors.joining(" "));

        Map<String, Value> payload = new HashMap<>();

        payload.put(
                "doc_id",
                Value.newBuilder()
                        .setStringValue(documentId)
                        .build()
        );

        payload.put(
                "length",
                Value.newBuilder()
                        .setIntegerValue(length)
                        .build()
        );

        payload.put(
                TERMS_KEY,
                Value.newBuilder()
                        .setStringValue(encodedTerms)
                        .build()
        );

        payload.put(
                "is_doc_length",
                Value.newBuilder()
                        .setBoolValue(true)
                        .build()
        );

        return buildServicePoint(
                documentStatsPointId(documentId),
                payload
        );
    }

    public void upsertPointsBatch(
            String collection,
            List<PointStruct> points
    ) {
        if (points.isEmpty()) {
            return;
        }

        try {
            qdrantClient.upsertAsync(
                    collection,
                    points
            ).get();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Не удалось сохранить пакет BM25-статистики",
                    e
            );
        }
    }

    public void deletePointsBatch(
            String collection,
            Collection<String> pointIds
    ) {
        if (pointIds.isEmpty()) {
            return;
        }

        List<PointId> ids = pointIds.stream()
                .map(id -> PointId.newBuilder()
                        .setUuid(id)
                        .build())
                .toList();

        try {
            qdrantClient.deleteAsync(
                    collection,
                    ids
            ).get();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Не удалось удалить BM25-статистику",
                    e
            );
        }
    }

    public PointStruct buildServicePoint(
            String uuid,
            Map<String, Value> payload
    ) {
        return PointStruct.newBuilder()
                .setId(
                        PointId.newBuilder()
                                .setUuid(uuid)
                                .build()
                )
                .setVectors(
                        Vectors.newBuilder()
                                .setVector(
                                        Vector.newBuilder()
                                                .addData(1.0f)
                                                .build()
                                )
                                .build()
                )
                .putAllPayload(payload)
                .build();
    }

    public String getTermPointId(String term) {
        return termPointId(term);
    }

    public String getDocumentStatsPointId(
            String documentId
    ) {
        return documentStatsPointId(documentId);
    }

    public void invalidateGlobalStats(
            String statsCollection
    ) {
        globalStatsCache.remove(statsCollection);
    }

    public void cacheGlobalStats(
            String statsCollection,
            GlobalStatsMeta meta
    ) {
        globalStatsCache.put(
                statsCollection,
                new CachedGlobalStats(
                        meta,
                        System.currentTimeMillis()
                                + CACHE_TTL_MS
                )
        );
    }

    private String termPointId(String term) {
        return UUID.nameUUIDFromBytes(
                ("term:" + term).getBytes(
                        StandardCharsets.UTF_8
                )
        ).toString();
    }

    private String documentStatsPointId(
            String documentId
    ) {
        return UUID.nameUUIDFromBytes(
                ("document:" + documentId).getBytes(
                        StandardCharsets.UTF_8
                )
        ).toString();
    }
}