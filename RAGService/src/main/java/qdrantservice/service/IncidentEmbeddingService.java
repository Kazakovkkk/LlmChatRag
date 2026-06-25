package qdrantservice.service;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.CreateCollection;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.SparseIndexConfig;
import io.qdrant.client.grpc.Collections.SparseVectorConfig;
import io.qdrant.client.grpc.Collections.SparseVectorParams;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Collections.VectorsConfig;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.NamedVectors;
import io.qdrant.client.grpc.Points.PointId;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.PointVectors;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import io.qdrant.client.grpc.Points.ScrollPoints;
import io.qdrant.client.grpc.Points.ScrollResponse;
import io.qdrant.client.grpc.Points.SparseVector;
import io.qdrant.client.grpc.Points.Vectors;
import io.qdrant.client.grpc.Points.WithPayloadSelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import qdrantservice.config.BatchSearchRunner;
import qdrantservice.dto.ChunkingResult;
import qdrantservice.dto.ScoredDocument;
import qdrantservice.service.chunker.ChunkRouter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentEmbeddingService {

    private static final int SPARSE_REBUILD_BATCH_SIZE = 256;

    private final QdrantClient qdrantClient;
    private final PdfTextExtractor pdfTextExtractor;
    private final ChunkRouter chunkRouter;
    private final BM25Tokenizer bm25Tokenizer;
    private final HybridSearchService hybridSearchService;
    private final EmbeddingModel remoteEmbeddingModel;
    private final BatchSearchRunner batchSearchRunner;
    private final Set<String> initializedTenants =
            ConcurrentHashMap.newKeySet();

    private String getMainCollection(String hotelKey) {
        return "hotel_" + normalizeHotelKey(hotelKey);
    }

    private String getStatsCollection(String hotelKey) {
        return "bm25_stats_hotel_"
                + normalizeHotelKey(hotelKey);
    }

    private String normalizeHotelKey(String hotelKey) {
        return hotelKey.toLowerCase()
                .replaceAll("[^a-z0-9]", "_");
    }

    private void initTenantInfrastructureIfNeeded(
            String hotelKey
    ) {
        if (initializedTenants.contains(hotelKey)) {
            return;
        }

        synchronized (hotelKey.intern()) {
            if (initializedTenants.contains(hotelKey)) {
                return;
            }

            String mainCollection =
                    getMainCollection(hotelKey);

            String statsCollection =
                    getStatsCollection(hotelKey);

            try {
                List<String> activeCollections =
                        qdrantClient.listCollectionsAsync()
                                .get();

                if (!activeCollections.contains(
                        mainCollection
                )) {
                    createMainCollection(mainCollection);
                }

                if (!activeCollections.contains(
                        statsCollection
                )) {
                    createStatsCollection(statsCollection);
                }

                initializedTenants.add(hotelKey);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Не удалось инициализировать "
                                + "инфраструктуру отеля "
                                + hotelKey,
                        e
                );
            }
        }
    }

    private void createMainCollection(
            String collection
    ) throws Exception {
        log.info(
                "Создание основной коллекции {}",
                collection
        );

        qdrantClient.createCollectionAsync(
                CreateCollection.newBuilder()
                        .setCollectionName(collection)
                        .setVectorsConfig(
                                VectorsConfig.newBuilder()
                                        .setParams(
                                                VectorParams
                                                        .newBuilder()
                                                        .setSize(768)
                                                        .setDistance(
                                                                Distance.Cosine
                                                        )
                                                        .build()
                                        )
                                        .build()
                        )
                        .setSparseVectorsConfig(
                                SparseVectorConfig.newBuilder()
                                        .putMap(
                                                "sparse",
                                                SparseVectorParams
                                                        .newBuilder()
                                                        .setIndex(
                                                                SparseIndexConfig
                                                                        .newBuilder()
                                                                        .setFullScanThreshold(
                                                                                5000
                                                                        )
                                                                        .build()
                                                        )
                                                        .build()
                                        )
                                        .build()
                        )
                        .build()
        ).get();
    }

    private void createStatsCollection(
            String collection
    ) throws Exception {
        log.info(
                "Создание коллекции BM25-статистики {}",
                collection
        );

        qdrantClient.createCollectionAsync(
                CreateCollection.newBuilder()
                        .setCollectionName(collection)
                        .setVectorsConfig(
                                VectorsConfig.newBuilder()
                                        .setParams(
                                                VectorParams
                                                        .newBuilder()
                                                        .setSize(1)
                                                        .setDistance(
                                                                Distance.Cosine
                                                        )
                                                        .build()
                                        )
                                        .build()
                        )
                        .build()
        ).get();
    }

    public void storeIncident(
            String hotelKey,
            String text,
            List<String> tags
    ) {
        initTenantInfrastructureIfNeeded(hotelKey);

        String mainCollection =
                getMainCollection(hotelKey);

        String statsCollection =
                getStatsCollection(hotelKey);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tags", tags);
        metadata.put(
                "timestamp",
                System.currentTimeMillis()
        );
        metadata.put("tenant_id", hotelKey);
        metadata.put("source", "incident");

        Document document = new Document(
                text,
                metadata
        );

        QdrantVectorStore store =
                createVectorStore(mainCollection);

        store.doAdd(List.of(document));

        bm25Tokenizer.upsertDocument(
                statsCollection,
                document.getId(),
                text
        );

        rebuildSparseVectors(
                mainCollection,
                statsCollection
        );
    }

    public List<Document> searchSimilarIncidents(
            String hotelKey,
            String query,
            Integer limit,
            String searchType
    ) {
        long start = System.currentTimeMillis();

        initTenantInfrastructureIfNeeded(hotelKey);

        String mainCollection =
                getMainCollection(hotelKey);

        String statsCollection =
                getStatsCollection(hotelKey);

        String normalizedSearchType =
                searchType == null
                        ? "vector"
                        : searchType.toLowerCase().trim();

        List<ScoredDocument> scoredDocuments =
                switch (normalizedSearchType) {
                    case "keyword" ->
                            hybridSearchService.searchByKeyword(
                                    mainCollection,
                                    statsCollection,
                                    query,
                                    limit
                            );

                    case "hybrid" ->
                            hybridSearchService.searchHybrid(
                                    mainCollection,
                                    statsCollection,
                                    query,
                                    limit,
                                    0.6f
                            );

                    default ->
                            hybridSearchService.searchByVector(
                                    mainCollection,
                                    query,
                                    limit,
                                    0.7f
                            );
                };

        List<Document> results =
                toDocuments(scoredDocuments);

        log.info(
                "Поиск hotel={}, type={}, duration={} ms, results={}",
                hotelKey,
                normalizedSearchType,
                System.currentTimeMillis() - start,
                results.size()
        );

        return results;
    }

    public void storePdfDocument(
            String hotelKey,
            byte[] pdfBytes,
            List<String> tags,
            String mode
    ) throws IOException {
        initTenantInfrastructureIfNeeded(hotelKey);

        String mainCollection =
                getMainCollection(hotelKey);

        String statsCollection =
                getStatsCollection(hotelKey);

        if ("OVERWRITE".equalsIgnoreCase(mode)) {
            clearTenantData(
                    hotelKey,
                    mainCollection,
                    statsCollection
            );
        }

        String cleanText =
                pdfTextExtractor.extractText(pdfBytes);

        ChunkingResult result =
                chunkRouter.processText(cleanText);

        QdrantVectorStore store =
                createVectorStore(mainCollection);

        List<Document> documents =
                new ArrayList<>();

        for (String chunk : result.chunks()) {
            Map<String, Object> metadata =
                    new HashMap<>();

            metadata.put("tags", tags);
            metadata.put("source", "pdf");
            metadata.put("tenant_id", hotelKey);

            documents.add(
                    new Document(chunk, metadata)
            );
        }

        if (documents.isEmpty()) {
            return;
        }

        store.doAdd(documents);


        for (Document document : documents) {
            bm25Tokenizer.upsertDocument(
                    statsCollection,
                    document.getId(),
                    document.getText()
            );
        }

        rebuildSparseVectors(
                mainCollection,
                statsCollection
        );

        log.info(
                "Сохранено {} PDF-чанков для отеля {}",
                documents.size(),
                hotelKey
        );
    }

    @Async
    public CompletableFuture<Void> storePdfDocumentAsync(
            String hotelKey,
            byte[] pdfBytes,
            List<String> tags,
            String mode
    ) throws IOException {
        storePdfDocument(
                hotelKey,
                pdfBytes,
                tags,
                mode
        );

        return CompletableFuture.completedFuture(null);
    }

    public List<Document> getAllChunks(
            String hotelKey,
            int limit
    ) {
        initTenantInfrastructureIfNeeded(hotelKey);

        String mainCollection =
                getMainCollection(hotelKey);

        try {
            ScrollResponse response =
                    qdrantClient.scrollAsync(
                            ScrollPoints.newBuilder()
                                    .setCollectionName(
                                            mainCollection
                                    )
                                    .setLimit(limit)
                                    .setWithPayload(
                                            WithPayloadSelector
                                                    .newBuilder()
                                                    .setEnable(true)
                                                    .build()
                                    )
                                    .build()
                    ).get();

            return response.getResultList()
                    .stream()
                    .map(this::toDocument)
                    .toList();
        } catch (Exception e) {
            log.error(
                    "Ошибка чтения чанков отеля {}",
                    hotelKey,
                    e
            );

            return List.of();
        }
    }

    public void updateChunkText(
            String hotelKey,
            String chunkId,
            String newText
    ) {
        initTenantInfrastructureIfNeeded(hotelKey);

        String mainCollection =
                getMainCollection(hotelKey);

        String statsCollection =
                getStatsCollection(hotelKey);

        try {
            float[] denseVector =
                    remoteEmbeddingModel.embed(
                            new Document(newText)
                    );

            List<Float> vectorValues =
                    new ArrayList<>(denseVector.length);

            for (float value : denseVector) {
                vectorValues.add(value);
            }

            Map<String, Value> payload =
                    new HashMap<>();

            payload.put(
                    "doc_content",
                    Value.newBuilder()
                            .setStringValue(newText)
                            .build()
            );

            payload.put(
                    "tenant_id",
                    Value.newBuilder()
                            .setStringValue(hotelKey)
                            .build()
            );

            payload.put(
                    "source",
                    Value.newBuilder()
                            .setStringValue("pdf_edited")
                            .build()
            );

            PointStruct updatedPoint =
                    PointStruct.newBuilder()
                            .setId(
                                    PointId.newBuilder()
                                            .setUuid(chunkId)
                                            .build()
                            )
                            .setVectors(
                                    Vectors.newBuilder()
                                            .setVector(
                                                    Points.Vector
                                                            .newBuilder()
                                                            .addAllData(
                                                                    vectorValues
                                                            )
                                                            .build()
                                            )
                                            .build()
                            )
                            .putAllPayload(payload)
                            .build();

            qdrantClient.upsertAsync(
                    mainCollection,
                    List.of(updatedPoint)
            ).get();

            bm25Tokenizer.upsertDocument(
                    statsCollection,
                    chunkId,
                    newText
            );

            rebuildSparseVectors(
                    mainCollection,
                    statsCollection
            );

            log.info(
                    "Чанк {} успешно обновлён",
                    chunkId
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Не удалось обновить чанк "
                            + chunkId,
                    e
            );
        }
    }

    public void deleteChunk(
            String hotelKey,
            String chunkId
    ) {
        initTenantInfrastructureIfNeeded(hotelKey);

        String mainCollection =
                getMainCollection(hotelKey);

        String statsCollection =
                getStatsCollection(hotelKey);

        try {
            qdrantClient.deleteAsync(
                    mainCollection,
                    List.of(
                            PointId.newBuilder()
                                    .setUuid(chunkId)
                                    .build()
                    )
            ).get();

            bm25Tokenizer.removeDocument(
                    statsCollection,
                    chunkId
            );

            rebuildSparseVectors(
                    mainCollection,
                    statsCollection
            );

            log.info(
                    "Чанк {} удалён",
                    chunkId
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Не удалось удалить чанк "
                            + chunkId,
                    e
            );
        }
    }

    private void clearTenantData(
            String hotelKey,
            String mainCollection,
            String statsCollection
    ) {
        try {
            Points.Filter tenantFilter =
                    Points.Filter.newBuilder()
                            .addMust(
                                    Points.Condition.newBuilder()
                                            .setField(
                                                    Points.FieldCondition
                                                            .newBuilder()
                                                            .setKey(
                                                                    "tenant_id"
                                                            )
                                                            .setMatch(
                                                                    Points.Match
                                                                            .newBuilder()
                                                                            .setKeyword(
                                                                                    hotelKey
                                                                            )
                                                                            .build()
                                                            )
                                                            .build()
                                            )
                                            .build()
                            )
                            .build();

            qdrantClient.deleteAsync(
                    mainCollection,
                    tenantFilter
            ).get();

            bm25Tokenizer.clearCollectionStats(
                    statsCollection
            );

            log.info(
                    "Данные отеля {} очищены",
                    hotelKey
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Не удалось очистить данные отеля "
                            + hotelKey,
                    e
            );
        }
    }

    private void rebuildSparseVectors(
            String mainCollection,
            String statsCollection
    ) {
        PointId offset = null;
        int updatedDocuments = 0;

        try {
            while (true) {
                ScrollPoints.Builder request =
                        ScrollPoints.newBuilder()
                                .setCollectionName(
                                        mainCollection
                                )
                                .setLimit(
                                        SPARSE_REBUILD_BATCH_SIZE
                                )
                                .setWithPayload(
                                        WithPayloadSelector
                                                .newBuilder()
                                                .setEnable(true)
                                                .build()
                                );

                if (offset != null) {
                    request.setOffset(offset);
                }

                ScrollResponse response =
                        qdrantClient.scrollAsync(
                                request.build()
                        ).get();

                List<PointVectors> sparseVectors =
                        new ArrayList<>();

                for (RetrievedPoint point
                        : response.getResultList()) {
                    Value textValue =
                            point.getPayloadMap()
                                    .get("doc_content");

                    if (textValue == null) {
                        continue;
                    }

                    String text =
                            textValue.getStringValue();

                    sparseVectors.add(
                            prepareSparseVector(
                                    statsCollection,
                                    point.getId().getUuid(),
                                    text
                            )
                    );
                }

                if (!sparseVectors.isEmpty()) {
                    qdrantClient.updateVectorsAsync(
                            mainCollection,
                            sparseVectors
                    ).get();

                    updatedDocuments +=
                            sparseVectors.size();
                }

                if (!response.hasNextPageOffset()) {
                    break;
                }

                PointId nextOffset =
                        response.getNextPageOffset();

                if (offset != null
                        && offset.equals(nextOffset)) {
                    throw new IllegalStateException(
                            "Qdrant вернул повторяющийся "
                                    + "scroll offset"
                    );
                }

                offset = nextOffset;
            }

            log.info(
                    "Пересчитано {} sparse-векторов коллекции {}",
                    updatedDocuments,
                    mainCollection
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Не удалось пересчитать sparse-векторы "
                            + mainCollection,
                    e
            );
        }
    }

    private PointVectors prepareSparseVector(
            String statsCollection,
            String documentId,
            String text
    ) {
        Map<Integer, Float> sparseVector =
                bm25Tokenizer.tokenizeWithBM25(
                        statsCollection,
                        text,
                        documentId,
                        true
                );

        List<Integer> indices =
                new ArrayList<>(sparseVector.keySet());

        List<Float> values = indices.stream()
                .map(sparseVector::get)
                .toList();

        NamedVectors namedVectors =
                NamedVectors.newBuilder()
                        .putVectors(
                                "sparse",
                                Points.Vector.newBuilder()
                                        .setSparse(
                                                SparseVector
                                                        .newBuilder()
                                                        .addAllValues(
                                                                values
                                                        )
                                                        .addAllIndices(
                                                                indices
                                                        )
                                                        .build()
                                        )
                                        .build()
                        )
                        .build();

        return PointVectors.newBuilder()
                .setId(
                        PointId.newBuilder()
                                .setUuid(documentId)
                                .build()
                )
                .setVectors(
                        Vectors.newBuilder()
                                .setVectors(namedVectors)
                                .build()
                )
                .build();
    }

    private QdrantVectorStore createVectorStore(
            String collection
    ) {
        return QdrantVectorStore.builder(
                        qdrantClient,
                        remoteEmbeddingModel
                )
                .collectionName(collection)
                .initializeSchema(false)
                .build();
    }
    public List<Document> searchSimilarIncidentsBatch(
            String hotelKey,
            List<String> queries,
            Integer limit
    ) {
        long start = System.currentTimeMillis();

        initTenantInfrastructureIfNeeded(hotelKey);

        String mainCollection = getMainCollection(hotelKey);
        String statsCollection = getStatsCollection(hotelKey);

        int resultLimit = limit != null && limit > 0
                ? limit
                : 5;

        List<String> normalizedQueries = queries == null
                ? List.of()
                : queries.stream()
                .filter(query -> query != null && !query.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        if (normalizedQueries.isEmpty()) {
            return List.of();
        }

        List<Callable<List<ScoredDocument>>> tasks = normalizedQueries .stream()
                .map(query -> (Callable<List<ScoredDocument>>) () ->
                        hybridSearchService.searchHybrid(
                                mainCollection,
                                statsCollection,
                                query,
                                limit,
                                0.7f
                        )
                )
                .toList();

        List<List<ScoredDocument>> searchResults =
                batchSearchRunner.execute(
                        tasks,
                        Duration.ofSeconds(5)
                );

        Map<String, Double> scoresById = new HashMap<>();
        Map<String, String> textById = new HashMap<>();

        for (List<ScoredDocument> queryResult : searchResults) {
            for (ScoredDocument document : queryResult) {
                scoresById.merge(
                        document.getId(),
                        (double) document.getScore(),
                        Double::sum
                );

                textById.putIfAbsent(
                        document.getId(),
                        document.getText()
                );
            }
        }

        List<ScoredDocument> mergedResults =
                scoresById.entrySet()
                        .stream()
                        .sorted(
                                Map.Entry
                                        .<String, Double>comparingByValue()
                                        .reversed()
                        )
                        .limit(resultLimit)
                        .map(entry -> new ScoredDocument(
                                entry.getKey(),
                                textById .getOrDefault(
                                        entry.getKey(),
                                        ""
                                ),
                                entry.getValue().floatValue(),
                                "hybrid-batch"
                        ))
                        .toList();

        log.info(
                "Batch-поиск hotel={}, queries={}, duration={} ms, results={}",
                hotelKey,
                normalizedQueries.size(),
                System.currentTimeMillis() - start,
                mergedResults.size()
        );

        return toDocuments(mergedResults);
    }

    private List<Document> toDocuments(
            List<ScoredDocument> scoredDocuments
    ) {
        return scoredDocuments.stream()
                .map(scoredDocument -> {
                    Map<String, Object> metadata =
                            new HashMap<>();

                    metadata.put(
                            "searchType",
                            scoredDocument.getSearchType()
                    );

                    String text =
                            scoredDocument.getText() == null
                                    ? ""
                                    : scoredDocument.getText();

                    return Document.builder()
                            .id(scoredDocument.getId())
                            .text(text)
                            .metadata(metadata)
                            .score(
                                    (double) scoredDocument.getScore()
                            )
                            .build();
                })
                .collect(Collectors.toList());
    }

    private Document toDocument(
            RetrievedPoint point
    ) {
        Value textValue =
                point.getPayloadMap()
                        .get("doc_content");

        String text = textValue == null
                ? ""
                : textValue.getStringValue();

        Map<String, Object> metadata =
                new HashMap<>();

        point.getPayloadMap().forEach(
                (key, value) ->
                        metadata.put(
                                key,
                                payloadValueToObject(value)
                        )
        );

        return new Document(
                point.getId().getUuid(),
                text,
                metadata
        );
    }

    private Object payloadValueToObject(Value value) {
        return switch (value.getKindCase()) {
            case STRING_VALUE ->
                    value.getStringValue();

            case INTEGER_VALUE ->
                    value.getIntegerValue();

            case DOUBLE_VALUE ->
                    value.getDoubleValue();

            case BOOL_VALUE ->
                    value.getBoolValue();

            case LIST_VALUE ->
                    value.getListValue()
                            .getValuesList()
                            .stream()
                            .map(this::payloadValueToObject)
                            .toList();

            case STRUCT_VALUE ->
                    value.getStructValue()
                            .getFieldsMap()
                            .entrySet()
                            .stream()
                            .collect(
                                    Collectors.toMap(
                                            Map.Entry::getKey,
                                            entry ->
                                                    payloadValueToObject(
                                                            entry.getValue()
                                                    )
                                    )
                            );

            case NULL_VALUE, KIND_NOT_SET ->
                    null;
        };
    }
}