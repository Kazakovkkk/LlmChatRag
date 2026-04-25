package qdrantservice.service;

import qdrantservice.dto.ChunkingResult;
import qdrantservice.dto.ScoredDocument;
import qdrantservice.service.chunker.ChunkRouter;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;


import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class IncidentEmbeddingService {

    private final QdrantVectorStore qdrantVectorStore;
    private final QdrantClient qdrantClient;
    private final PdfTextExtractor pdfTextExtractor;
    private final ChunkRouter chunkRouter;
    private final BM25Tokenizer bm25Tokenizer;
    private final HybridSearchService hybridSearchService;
    private final PdfReportService pdfReportService;

    @Value("${qdrant.collection-name:incidents}")
    private String collectionName;
    @Value("${qdrant.search-type:vector}")
    private String configuredSearchType;

    public IncidentEmbeddingService(
            QdrantVectorStore qdrantVectorStore,
            QdrantClient qdrantClient,
            PdfTextExtractor pdfTextExtractor,
            ChunkRouter chunkRouter,
            BM25Tokenizer bm25Tokenizer,
            HybridSearchService hybridSearchService,
            PdfReportService pdfReportService) {


        this.qdrantVectorStore = qdrantVectorStore;
        this.qdrantClient = qdrantClient;
        this.pdfTextExtractor = pdfTextExtractor;
        this.chunkRouter = chunkRouter;
        this.bm25Tokenizer = bm25Tokenizer;
        this.hybridSearchService = hybridSearchService;
        this.pdfReportService = pdfReportService;
    }

    // ─── Сохранение одного документа ──────────────────────────

    public void storeIncident(String text, List<String> tags) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tags", tags);
        metadata.put("timestamp", System.currentTimeMillis());

        Document document = new Document(text, metadata);
        qdrantVectorStore.doAdd(List.of(document));

        // Индексируем для BM25 перед генерацией sparse вектора
        bm25Tokenizer.indexDocument(document.getId(), text);
        storeSparseVector(document.getId(), text);

        log.info("Документ сохранён: '{}'", text.substring(0, Math.min(50, text.length())));
    }

    // ─── Поиск документов ─────────────────────────────────────

    public List<Document> searchSimilarIncidents(String query, Integer limit) {
        return searchSimilarIncidents(query, limit, configuredSearchType);
    }

    public List<Document> searchSimilarIncidents(String query, Integer limit, String searchType) {
        long start = System.currentTimeMillis();

        // Если из контроллера пришел null или пустая строка, берем значение из конфига
        String activeSearchType = (searchType == null || searchType.isBlank())
                ? configuredSearchType
                : searchType;

        List<Document> results = switch (activeSearchType.toLowerCase().trim()) {
            case "keyword" -> {
                List<ScoredDocument> docs = hybridSearchService.searchByKeyword(query, limit);
                yield toDocuments(docs);
            }
            case "hybrid" -> {
                List<ScoredDocument> docs = hybridSearchService.searchHybrid(query, limit, 0.8f);
                yield toDocuments(docs);
            }
            default -> { // Включая "vector"
                SearchRequest searchRequest = SearchRequest.builder()
                        .query(query)
                        .topK(limit)
                        .similarityThreshold(0.8)
                        .build();
                yield qdrantVectorStore.similaritySearch(searchRequest);
            }
        };

        log.info("⏱ Поиск [{}]: {} мс | {} результатов",
                activeSearchType, System.currentTimeMillis() - start, results.size());

        return results;
    }

    // ─── Сохранение PDF ───────────────────────────────────────

    public void storePdfDocument(byte[] pdfBytes, List<String> tags) throws IOException {
        long totalStart = System.currentTimeMillis();
        String filename = tags.isEmpty() ? "document" : tags.get(0);

        // Шаг 1: Извлекаем и очищаем текст
        long t1 = System.currentTimeMillis();
        String cleanText = pdfTextExtractor.extractText(pdfBytes);
        log.info("⏱ Извлечение текста: {} мс | {} символов",
                System.currentTimeMillis() - t1, cleanText.length());

        // ← Генерируем PDF 1: очищенный текст
        byte[] cleanTextPdf = pdfReportService.generateCleanTextPdf(cleanText, filename);
        log.info("✅ PDF 1 создан: очищенный текст ({} байт)", cleanTextPdf.length);

        // Шаг 2: Разбиваем на предложения
        long tRouter = System.currentTimeMillis();
        ChunkingResult result = chunkRouter.processText(cleanText);
        List<String> chunks = result.chunks();
        log.info("⏱ Чанкование завершено: {} мс | {} чанков",
                System.currentTimeMillis() - tRouter, chunks.size());

// ← Генерируем PDF 2: предложения с косинусным сходством (ТОЛЬКО ЕСЛИ ЕСТЬ ДАННЫЕ)
        byte[] sentencesPdf = new byte[0];
        if (!result.sentences().isEmpty() && !result.embeddings().isEmpty()) {
            sentencesPdf = pdfReportService.generateSentencesSimilarityPdf(
                    result.sentences(), result.embeddings(), filename);
            log.info("✅ PDF 2 создан: предложения + сходство ({} байт)", sentencesPdf.length);
        } else {
            log.info("⏩ PDF 2 пропущен (не поддерживается для текущей стратегии чанкования)");
        }

// ← Генерируем PDF 3: чанки
        byte[] chunksPdf = pdfReportService.generateChunksPdf(chunks, filename);
        log.info("✅ PDF 3 создан: чанки ({} байт)", chunksPdf.length);

// Сохраняем PDF отчёты (проверяем, чтобы не сохранять пустые)
        savePdfReports(filename, cleanTextPdf, sentencesPdf, chunksPdf);

        // Шаг 4: Сохраняем каждый чанк
        long t4 = System.currentTimeMillis();
        List<PointVectors> sparseBatch = new ArrayList<>();
        int saved = 0;
        int failed = 0;

        for (String chunk : chunks) {
            try {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("tags", tags);
                metadata.put("timestamp", System.currentTimeMillis());
                metadata.put("source", "pdf");

                Document doc = new Document(chunk, metadata);
                qdrantVectorStore.doAdd(List.of(doc));

                // Индексируем для BM25
                bm25Tokenizer.indexDocument(doc.getId(), chunk);

                sparseBatch.add(prepareSparseVector(doc.getId(), chunk));
                saved++;
            } catch (Exception e) {
                log.error("Ошибка сохранения чанка: {}", e.getMessage());
                failed++;
            }
        }
        if (!sparseBatch.isEmpty()) {
            try {
                log.info("Отправка пакета из {} sparse векторов...", sparseBatch.size());
                qdrantClient.updateVectorsAsync(collectionName, sparseBatch).get();
                log.info("Пакет успешно сохранен");
            } catch (Exception e) {
                log.error("Критическая ошибка при пакетном сохранении: {}", e.getMessage());
            }
        }

        log.info("⏱ Сохранение в Qdrant: {} мс",
                System.currentTimeMillis() - t4);
        log.info("⏱ ИТОГО обработка PDF: {} мс", System.currentTimeMillis() - totalStart);
        bm25Tokenizer.persistStats();
        bm25Tokenizer.logStats();
    }

    // ─── Вспомогательные методы ───────────────────────────────

    private void storeSparseVector(String documentId, String text) {
        try {
            List<String> terms = bm25Tokenizer.tokenizeToTerms(text);
            log.info("Сохранение sparse | ID: {} | Термины: {}",
                    documentId.substring(0, 8), terms);
            Map<Integer, Float> sparseVectorMap = bm25Tokenizer.tokenize(text);

            List<Integer> indices = new ArrayList<>(sparseVectorMap.keySet());
            List<Float> values = indices.stream()
                    .map(sparseVectorMap::get)
                    .collect(Collectors.toList());

            // 1. Собираем NamedVectors
            NamedVectors namedVectors = NamedVectors.newBuilder()
                    .putVectors("sparse",
                            io.qdrant.client.grpc.Points.Vector.newBuilder()
                                    .setSparse(SparseVector.newBuilder()
                                            .addAllValues(values)
                                            .addAllIndices(indices)
                                            .build())
                                    .build())
                    .build();

            // 2. Собираем объект PointVectors (ID точки + векторы)
            PointVectors pointVectors = PointVectors.newBuilder()
                    .setId(PointId.newBuilder().setUuid(documentId).build())
                    .setVectors(Vectors.newBuilder()
                            .setVectors(namedVectors)
                            .build())
                    .build();

            // 3. Отправляем обновление в Qdrant Client напрямую по имени коллекции
            qdrantClient.updateVectorsAsync(
                    collectionName,           // Имя коллекции передаем первым аргументом
                    List.of(pointVectors)     // Список обновляемых точек вторым
            ).get();

            log.debug("Sparse вектор успешно добавлен к документу: {}", documentId);

        } catch (Exception e) {
            log.error("Ошибка при обновлении векторов для {}: {}", documentId, e.getMessage());
        }
    }

    private PointVectors prepareSparseVector(String documentId, String text) {
        Map<Integer, Float> sparseVectorMap = bm25Tokenizer.tokenize(text);

        List<Integer> indices = new ArrayList<>(sparseVectorMap.keySet());
        List<Float> values = indices.stream()
                .map(sparseVectorMap::get)
                .collect(Collectors.toList());

        NamedVectors namedVectors = NamedVectors.newBuilder()
                .putVectors("sparse",
                        io.qdrant.client.grpc.Points.Vector.newBuilder()
                                .setSparse(SparseVector.newBuilder()
                                        .addAllValues(values)
                                        .addAllIndices(indices)
                                        .build())
                                .build())
                .build();

        return PointVectors.newBuilder()
                .setId(PointId.newBuilder().setUuid(documentId).build())
                .setVectors(Vectors.newBuilder().setVectors(namedVectors).build())
                .build();
    }
    @Async
    public CompletableFuture<Void> storePdfDocumentAsync(byte[] pdfBytes, List<String> tags) throws IOException {
        storePdfDocument(pdfBytes, tags);
        return CompletableFuture.completedFuture(null);
    }

    private List<Document> toDocuments(List<ScoredDocument> scoredDocs) {
        return scoredDocs.stream()
                .map(sd -> {
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("score", sd.getScore());
                    log.info("score: {}", sd.getScore());
                    meta.put("searchType", sd.getSearchType());
                    return new Document(sd.getText(), meta);
                })
                .collect(Collectors.toList());
    }
    private void savePdfReports(String filename,
                                byte[] cleanTextPdf,
                                byte[] sentencesPdf,
                                byte[] chunksPdf) {
        try {
            java.nio.file.Path reportsDir = java.nio.file.Paths.get("reports");
            java.nio.file.Files.createDirectories(reportsDir);

            java.nio.file.Files.write(
                    reportsDir.resolve(filename + "_1_clean_text.pdf"), cleanTextPdf);
            java.nio.file.Files.write(
                    reportsDir.resolve(filename + "_2_sentences.pdf"), sentencesPdf);
            java.nio.file.Files.write(
                    reportsDir.resolve(filename + "_3_chunks.pdf"), chunksPdf);

            log.info("✅ Все 3 PDF отчёта сохранены в директории: reports/");
        } catch (Exception e) {
            log.error("Ошибка сохранения PDF отчётов: {}", e.getMessage());
        }
    }
}