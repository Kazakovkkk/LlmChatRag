package qdrantservice.service.chunker;

import qdrantservice.dto.ChunkingResult;
import qdrantservice.service.EmbeddingRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component

public class SemanticChunker {


    private static final double SIMILARITY_THRESHOLD = 0.79;
    private static final int MIN_SENTENCES_PER_CHUNK = 2;
    private static final int MAX_SENTENCES_PER_CHUNK = 8;

    private final EmbeddingRouter embeddingRouter;

    @Value("${chunker.embedding.mode:single}")
    private String embeddingMode;

    public SemanticChunker(EmbeddingRouter embeddingRouter) {
        this.embeddingRouter = embeddingRouter;
    }

    public List<String> chunk(List<String> sentences) {
        if (sentences.isEmpty()) return List.of();
        if (sentences.size() == 1) return sentences;

        log.info("⏱ Режим эмбеддингов: {} | {} предложений", embeddingMode, sentences.size());
        long start = System.currentTimeMillis();

        List<float[]> embeddings = embeddingMode.equals("batch")
                ? getEmbeddingsBatch(sentences)
                : getEmbeddingsSingle(sentences);

        log.info("⏱ Получение эмбеддингов ({}): {} мс",
                embeddingMode, System.currentTimeMillis() - start);

        return buildChunks(sentences, embeddings);
    }


    private List<float[]> getEmbeddingsSingle(List<String> sentences) {
        log.info("→ Single mode: {} запросов к embedding-service", sentences.size());
        return sentences.stream()
                .map(embeddingRouter::embed)
                .toList();
    }


    private List<float[]> getEmbeddingsBatch(List<String> sentences) {
        log.info("→ Batch mode: 1 запрос к embedding-service на {} предложений", sentences.size());
        return embeddingRouter.embedBatch(sentences);
    }


    private List<String> buildChunks(List<String> sentences, List<float[]> embeddings) {
        List<String> chunks = new ArrayList<>();
        List<String> currentChunk = new ArrayList<>();
        currentChunk.add(sentences.get(0));

        for (int i = 1; i < sentences.size(); i++) {
            float[] prevEmbedding = embeddings.get(i - 1);
            float[] currEmbedding = embeddings.get(i);

            double similarity = cosineSimilarity(prevEmbedding, currEmbedding);
            log.debug("Схожесть предложений {} и {}: {}", i - 1, i, similarity);

            boolean tooLarge = currentChunk.size() >= MAX_SENTENCES_PER_CHUNK;
            boolean lowSimilarity = similarity < SIMILARITY_THRESHOLD;
            boolean enoughSentences = currentChunk.size() >= MIN_SENTENCES_PER_CHUNK;

            if ((lowSimilarity || tooLarge) && enoughSentences) {
                chunks.add(String.join(" ", currentChunk));
                log.info("Создан чанк #{} из {} предложений", chunks.size(), currentChunk.size());
                currentChunk = new ArrayList<>();
            }

            currentChunk.add(sentences.get(i));
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(String.join(" ", currentChunk));
            log.info("Создан последний чанк #{} из {} предложений",
                    chunks.size(), currentChunk.size());
        }

        List<String> filteredChunks = chunks.stream()
                .filter(chunk -> chunk.split("\\s+").length >= 10)
                .filter(chunk -> chunk.length() >= 50)
                .collect(Collectors.toList());

        log.info("Чанков после фильтрации: {} (было {})", filteredChunks.size(), chunks.size());
        return filteredChunks;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    public ChunkingResult chunkWithDetails(List<String> sentences) {
        if (sentences.isEmpty()) return new ChunkingResult(List.of(), List.of(), List.of());

        log.info("⏱ Режим эмбеддингов: {} | {} предложений", embeddingMode, sentences.size());
        long start = System.currentTimeMillis();

        List<float[]> embeddings = embeddingMode.equals("batch")
                ? getEmbeddingsBatch(sentences)
                : getEmbeddingsSingle(sentences);

        log.info("⏱ Получение эмбеддингов ({}): {} мс",
                embeddingMode, System.currentTimeMillis() - start);

        List<String> chunks = buildChunks(sentences, embeddings);

        return new ChunkingResult(chunks, sentences, embeddings);
    }
}