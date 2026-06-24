package qdrantservice.model;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class RemoteEmbeddingModel extends AbstractEmbeddingModel {

    private final RestClient embeddingRestClient;

    public RemoteEmbeddingModel(
            @Qualifier("embeddingRestClient")
            RestClient embeddingRestClient
    ) {
        this.embeddingRestClient = embeddingRestClient;
    }

    /*
     * Spring AI вызывает этот метод при store.doAdd(documents).
     * Все элементы здесь являются документами.
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<float[]> vectors = embedPassages(
                request.getInstructions()
        );

        List<Embedding> embeddings =
                new ArrayList<>(vectors.size());

        for (int i = 0; i < vectors.size(); i++) {
            embeddings.add(
                    new Embedding(vectors.get(i), i)
            );
        }

        return new EmbeddingResponse(embeddings);
    }

    /*
     * SemanticChunker передаёт обычные строки документов.
     */
    @Override
    public float[] embed(String text) {
        return embedPassage(text);
    }

    @Override
    public float[] embed(Document document) {
        return embedPassage(
                document.getFormattedContent()
        );
    }

    public float[] embedQuery(String text) {
        return requestEmbedding(
                withPrefix("query: ", text),
                "query"
        );
    }

    public float[] embedPassage(String text) {
        return requestEmbedding(
                withPrefix("passage: ", text),
                "passage"
        );
    }

    public List<float[]> embedPassages(
            List<String> texts
    ) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<String> formatted = texts.stream()
                .map(text -> withPrefix(
                        "passage: ",
                        text
                ))
                .toList();

        List<float[]> vectors =
                embeddingRestClient.post()
                        .uri("/embed/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "texts", formatted,
                                "inputType", "passage"
                        ))
                        .retrieve()
                        .body(
                                new ParameterizedTypeReference<>() {
                                }
                        );

        validateBatchResult(
                texts.size(),
                vectors
        );

        return vectors;
    }

    private float[] requestEmbedding(
            String text,
            String inputType
    ) {
        float[] vector = embeddingRestClient.post()
                .uri("/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "text", text,
                        "inputType", inputType
                ))
                .retrieve()
                .body(float[].class);

        if (vector == null || vector.length == 0) {
            throw new IllegalStateException(
                    "EmbeddingService вернул пустой вектор"
            );
        }

        return vector;
    }

    private void validateBatchResult(
            int expectedSize,
            List<float[]> vectors
    ) {
        if (vectors == null) {
            throw new IllegalStateException(
                    "EmbeddingService вернул пустой batch"
            );
        }

        if (vectors.size() != expectedSize) {
            throw new IllegalStateException(
                    "Некорректный размер embedding batch: "
                            + "ожидалось " + expectedSize
                            + ", получено " + vectors.size()
            );
        }

        for (int i = 0; i < vectors.size(); i++) {
            float[] vector = vectors.get(i);

            if (vector == null || vector.length == 0) {
                throw new IllegalStateException(
                        "Пустой embedding с индексом " + i
                );
            }
        }
    }

    private String withPrefix(
            String prefix,
            String text
    ) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Embedding text is empty"
            );
        }

        String normalized = text.trim();

        if (normalized.startsWith("query: ")
                || normalized.startsWith("passage: ")) {
            return normalized;
        }

        return prefix + normalized;
    }
}