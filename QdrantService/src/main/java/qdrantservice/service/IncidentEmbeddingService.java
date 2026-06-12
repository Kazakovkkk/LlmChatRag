package qdrantservice.service;

import io.qdrant.client.grpc.Points;
import qdrantservice.dto.ChunkingResult;
import qdrantservice.dto.ScoredDocument;
import qdrantservice.service.chunker.ChunkRouter;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.*;
import io.qdrant.client.grpc.Points.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import io.qdrant.client.grpc.Points.ScrollPoints;
import io.qdrant.client.grpc.Points.ScrollResponse;
import io.qdrant.client.grpc.JsonWithInt.Value;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentEmbeddingService {

    private final QdrantClient qdrantClient;
    private final PdfTextExtractor pdfTextExtractor;
    private final ChunkRouter chunkRouter;
    private final BM25Tokenizer bm25Tokenizer;
    private final HybridSearchService hybridSearchService;
    private final EmbeddingModel remoteEmbeddingModel;

    // Генерация изолированных системных имен коллекций для Qdrant
    private String getMainCollection(String hotelKey) {
        return "hotel_" + hotelKey.toLowerCase().replaceAll("[^a-z0-9]", "_");
    }

    private String getStatsCollection(String hotelKey) {
        return "bm25_stats_hotel_" + hotelKey.toLowerCase().replaceAll("[^a-z0-9]", "_");
    }

    private void initTenantInfrastructureIfNeeded(String hotelKey) {
        String mainColl = getMainCollection(hotelKey);
        String statsColl = getStatsCollection(hotelKey);

        try {
            List<String> activeCollections = qdrantClient.listCollectionsAsync().get();

            if (!activeCollections.contains(mainColl)) {
                log.info("🚀 Разворачиваем основную коллекцию для нового тенанта: {}", mainColl);
                qdrantClient.createCollectionAsync(
                        CreateCollection.newBuilder()
                                .setCollectionName(mainColl)
                                .setVectorsConfig(VectorsConfig.newBuilder()
                                        .setParams(VectorParams.newBuilder().setSize(768).setDistance(Distance.Cosine).build())
                                        .build())
                                .setSparseVectorsConfig(SparseVectorConfig.newBuilder()
                                        .putMap("sparse", SparseVectorParams.newBuilder()
                                                .setIndex(SparseIndexConfig.newBuilder().setFullScanThreshold(5000).build())
                                                .build())
                                        .build())
                                .build()
                ).get();
            }

            if (!activeCollections.contains(statsColl)) {
                log.info("🚀 Разворачиваем служебную коллекцию лингвистической статистики для: {}", statsColl);
                qdrantClient.createCollectionAsync(
                        CreateCollection.newBuilder()
                                .setCollectionName(statsColl)
                                .setVectorsConfig(VectorsConfig.newBuilder()
                                        .setParams(VectorParams.newBuilder().setSize(1).setDistance(Distance.Cosine).build())
                                        .build())
                                .build()
                ).get();
            }
        } catch (Exception e) {
            throw new RuntimeException("Критическая ошибка инициализации SaaS инфраструктуры для " + hotelKey, e);
        }
    }

    public void storeIncident(String hotelKey, String text, List<String> tags) {
        initTenantInfrastructureIfNeeded(hotelKey);
        String mainColl = getMainCollection(hotelKey);
        String statsColl = getStatsCollection(hotelKey);

        Map<String, Object> metadata = Map.of("tags", tags, "timestamp", System.currentTimeMillis());
        Document document = new Document(text, metadata);

        // Инстанциируем адаптер Spring AI Vector Store строго под коллекцию отеля
        QdrantVectorStore store = QdrantVectorStore.builder(qdrantClient, remoteEmbeddingModel)
                .collectionName(mainColl)
                .initializeSchema(false)
                .build();

        store.doAdd(List.of(document));

        bm25Tokenizer.indexDocument(statsColl, document.getId(), text);
        storeSparseVector(mainColl, statsColl, document.getId(), text);
    }
    private List<Document> toDocuments(List<ScoredDocument> scoredDocs) {
        return scoredDocs.stream()
                .map(sd -> {
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("searchType", sd.getSearchType());
                    String textContent = sd.getText() != null ? sd.getText() : "";

                    return Document.builder()
                            .id(sd.getId())
                            .text(textContent)
                            .metadata(meta)
                            .score((double) sd.getScore())
                            .build();
                })
                .collect(Collectors.toList());
    }
    public List<Document> searchSimilarIncidents(String hotelKey, String query, Integer limit, String searchType) {
        long start = System.currentTimeMillis();
        initTenantInfrastructureIfNeeded(hotelKey);
        String mainColl = getMainCollection(hotelKey);
        String statsColl = getStatsCollection(hotelKey);
        var scoredDocs = switch (searchType.toLowerCase().trim()) {
            case "keyword" -> hybridSearchService.searchByKeyword(mainColl, statsColl, query, limit);
            case "hybrid" -> hybridSearchService.searchHybrid(mainColl, statsColl, query, limit, 0.7f);
            default -> hybridSearchService.searchByVector(mainColl, query, limit, 0.7f);
        };

        List<Document> results = toDocuments(scoredDocs);

        log.info("⏱ Поиск [Hotel: {}, Type: {}]: {} мс | {} результатов",
                hotelKey, searchType, System.currentTimeMillis() - start, results.size());

        return results;
    }
    public void storePdfDocument(String hotelKey, byte[] pdfBytes, List<String> tags, String mode) throws IOException {
        initTenantInfrastructureIfNeeded(hotelKey);
        String mainColl = getMainCollection(hotelKey);
        String statsColl = getStatsCollection(hotelKey);

        if ("OVERWRITE".equalsIgnoreCase(mode)) {
            log.warn("Обнаружен режим OVERWRITE для отеля {}. Начинается каскадная очистка старых чанков...", hotelKey);
            try {
                io.qdrant.client.grpc.Points.Filter tenantFilter = io.qdrant.client.grpc.Points.Filter.newBuilder()
                        .addMust(io.qdrant.client.grpc.Points.Condition.newBuilder()
                                .setField(io.qdrant.client.grpc.Points.FieldCondition.newBuilder()
                                        .setKey("tenant_id")
                                        .setMatch(io.qdrant.client.grpc.Points.Match.newBuilder()
                                                .setKeyword(hotelKey)
                                                .build())
                                        .build())
                                .build())
                        .build();
                qdrantClient.deleteAsync(mainColl, tenantFilter).get();
                log.info("Старые чанки отеля {} успешно удалены из коллекции Qdrant: {}", hotelKey, mainColl);

                bm25Tokenizer.clearCollectionStats(statsColl);
                log.info("Локальная статистика BM25 для коллекции {} успешно очищена", statsColl);

            } catch (Exception e) {
                log.error("Критическая ошибка при очистке пространства Qdrant перед OVERWRITE: {}", e.getMessage());
                throw new RuntimeException("Не удалось атомарно очистить старую базу знаний тенанта: " + e.getMessage(), e);
            }
        }
        String cleanText = pdfTextExtractor.extractText(pdfBytes);
        ChunkingResult result = chunkRouter.processText(cleanText);

        QdrantVectorStore store = QdrantVectorStore.builder(qdrantClient, remoteEmbeddingModel)
                .collectionName(mainColl)
                .initializeSchema(false)
                .build();

        List<PointVectors> sparseBatch = new ArrayList<>();

        for (String chunk : result.chunks()) {
            Map<String, Object> metadata = Map.of("tags", tags, "source", "pdf", "tenant_id", hotelKey);
            Document doc = new Document(chunk, metadata);

            store.doAdd(List.of(doc));
            String lockKey = hotelKey.intern();
            synchronized (lockKey) {
                bm25Tokenizer.indexDocument(statsColl, doc.getId(), chunk);
            }
            sparseBatch.add(prepareSparseVector(statsColl, doc.getId(), chunk));
        }

        // Пакетная заливка разреженных векторов в Qdrant
        if (!sparseBatch.isEmpty()) {
            try {
                qdrantClient.updateVectorsAsync(mainColl, sparseBatch).get();
                log.info("🚀 Успешно залито {} разреженных векторов для отеля {}", sparseBatch.size(), hotelKey);
            } catch (Exception e) {
                log.error("Ошибка пакетной заливки sparse векторов: {}", e.getMessage());
            }
        }
    }

    private void storeSparseVector(String mainColl, String statsColl, String documentId, String text) {
        try {
            PointVectors pv = prepareSparseVector(statsColl, documentId, text);
            qdrantClient.updateVectorsAsync(mainColl, List.of(pv)).get();
        } catch (Exception e) {
            log.error("Ошибка обновления sparse вектора: {}", e.getMessage());
        }
    }

    private PointVectors prepareSparseVector(String statsColl, String documentId, String text) {
        Map<Integer, Float> sparseVectorMap = bm25Tokenizer.tokenizeWithBM25(statsColl, text, documentId, true);
        List<Integer> indices = new ArrayList<>(sparseVectorMap.keySet());
        List<Float> values = indices.stream().map(sparseVectorMap::get).toList();

        NamedVectors namedVectors = NamedVectors.newBuilder()
                .putVectors("sparse", Points.Vector.newBuilder()
                        .setSparse(SparseVector.newBuilder().addAllValues(values).addAllIndices(indices).build())
                        .build())
                .build();

        return PointVectors.newBuilder()
                .setId(PointId.newBuilder().setUuid(documentId).build())
                .setVectors(Vectors.newBuilder().setVectors(namedVectors).build())
                .build();
    }
// Обнови этот метод в файле IncidentEmbeddingService.java

    @Async
    public CompletableFuture<Void> storePdfDocumentAsync(String hotelKey, byte[] pdfBytes, List<String> tags, String mode) throws IOException {
        // Вызывает наш обновленный storePdfDocument с 4-мя параметрами (где инкапсулирована логика чистки Qdrant)
        storePdfDocument(hotelKey, pdfBytes, tags, mode);
        return CompletableFuture.completedFuture(null);
    }
    /**
     * Выгрузка всех текстовых чанков отеля из Qdrant без векторного сравнения (для админ-панели)
     */
    public List<Document> getAllChunks(String hotelKey, int limit) {
        String mainColl = getMainCollection(hotelKey);
        initTenantInfrastructureIfNeeded(hotelKey);

        try {
            // Используем метод Scroll вместо Search, чтобы просто читать данные подряд
            ScrollResponse response = qdrantClient.scrollAsync(
                    ScrollPoints.newBuilder()
                            .setCollectionName(mainColl)
                            .setLimit(limit)
                            .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                            .build()
            ).get();

            return response.getResultList().stream().map(p -> {
                String text = p.getPayloadMap().containsKey("doc_content")
                        ? p.getPayloadMap().get("doc_content").getStringValue()
                        : "";

                Map<String, Object> meta = new HashMap<>();
                p.getPayloadMap().forEach((k, v) -> meta.put(k, v.getStringValue()));

                // Передаем UUID точки в качестве ID документа Spring AI
                return new Document(p.getId().getUuid(), text, meta);
            }).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Ошибка выгрузки базы знаний для отеля {}: {}", hotelKey, e.getMessage());
            return List.of();
        }
    }

    /**
     * Киллер-фича: Точечная мутация (обновление) одного чанка в Qdrant
     */
    public void updateChunkText(String hotelKey, String chunkId, String newText) {
        String mainColl = getMainCollection(hotelKey);
        String statsColl = getStatsCollection(hotelKey);
        initTenantInfrastructureIfNeeded(hotelKey);

        try {
            log.info("Старт точечной мутации чанка {}. Новый текст: {}", chunkId, newText);
            org.springframework.ai.document.Document docWrapper = new org.springframework.ai.document.Document(newText);
            float[] newDenseVector = remoteEmbeddingModel.embed(docWrapper);
            List<Float> vectorList = new ArrayList<>();
            for (float v : newDenseVector) vectorList.add(v);
            Map<String, Value> payload = new HashMap<>();
            payload.put("doc_content", Value.newBuilder().setStringValue(newText).build());
            payload.put("tenant_id", Value.newBuilder().setStringValue(hotelKey).build());
            payload.put("source", Value.newBuilder().setStringValue("pdf_edited").build());
            qdrantClient.upsertAsync(mainColl, List.of(
                    PointStruct.newBuilder()
                            .setId(PointId.newBuilder().setUuid(chunkId).build())
                            .setVectors(Vectors.newBuilder()
                                    .setVector(Points.Vector.newBuilder().addAllData(vectorList).build())
                                    .build())
                            .putAllPayload(payload)
                            .build()
            )).get();
            bm25Tokenizer.indexDocument(statsColl, chunkId, newText);
            PointVectors updatedSparseVector = prepareSparseVector(statsColl, chunkId, newText);
            qdrantClient.updateVectorsAsync(mainColl, List.of(updatedSparseVector)).get();

            log.info("Чанк {} успешно обновлен во всех пространствах (Dense + Sparse/BM25)", chunkId);

        } catch (Exception e) {
            log.error("Критический сбой мутации чанка {}: {}", chunkId, e.getMessage());
            throw new RuntimeException("Не удалось обновить чанк в Qdrant", e);
        }
    }
}