package embeddingservice.model;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RosbertaEmbeddingModel extends AbstractEmbeddingModel {

    private final RestClient restClient;
    private final int maxBatchSize;

    public RosbertaEmbeddingModel(
            @Qualifier("ruEnHuggingFaceRestClient")
            RestClient restClient,
            @Value("${huggingface.tei.max-batch-size:32}")
            int maxBatchSize
    ) {
        if (maxBatchSize < 1) {
            throw new IllegalArgumentException(
                    "TEI max batch size must be positive"
            );
        }

        this.restClient = restClient;
        this.maxBatchSize = maxBatchSize;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<float[]> vectors = embedBatch(
                request.getInstructions(),
                "query"
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

    @Override
    public float[] embed(Document document) {
        return embedOne(
                document.getFormattedContent(),
                "query"
        );
    }

    public float[] embedOne(
            String text,
            String inputType
    ) {
        return embedBatch(
                List.of(text),
                inputType
        ).getFirst();
    }

    public List<float[]> embedBatch(
            List<String> texts,
            String inputType
    ) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        long startTime = System.currentTimeMillis();

        String prefix = "query: ";

        List<String> formattedTexts = texts.stream()
                .map(this::validateText)
                .map(text -> hasE5Prefix(text)
                        ? text
                        : prefix + text)
                .toList();

        List<float[]> result =
                new ArrayList<>(formattedTexts.size());

        int requestCount = 0;

        for (int from = 0;
             from < formattedTexts.size();
             from += maxBatchSize) {

            int to = Math.min(
                    from + maxBatchSize,
                    formattedTexts.size()
            );

            List<String> batch = List.copyOf(
                    formattedTexts.subList(from, to)
            );

            List<List<Double>> response =
                    requestTeiBatch(batch);

            validateResponse(
                    batch.size(),
                    response
            );

            for (List<Double> vector : response) {
                result.add(toFloatArray(vector));
            }

            requestCount++;
        }

        if (result.size() != texts.size()) {
            throw new IllegalStateException(
                    "Некорректное итоговое количество векторов: "
                            + "ожидалось " + texts.size()
                            + ", получено " + result.size()
            );
        }

        log.info(
                "TEI embedding: texts={}, batches={}, duration={} ms",
                texts.size(),
                requestCount,
                System.currentTimeMillis() - startTime
        );

        return result;
    }

    private List<List<Double>> requestTeiBatch(
            List<String> batch
    ) {
        return restClient.post()
                .uri("/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("inputs", batch))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private void validateResponse(
            int expectedSize,
            List<List<Double>> response
    ) {
        if (response == null) {
            throw new IllegalStateException(
                    "TEI вернул пустой ответ"
            );
        }

        if (response.size() != expectedSize) {
            throw new IllegalStateException(
                    "Некорректный размер ответа TEI: "
                            + "ожидалось " + expectedSize
                            + ", получено " + response.size()
            );
        }

        for (int i = 0; i < response.size(); i++) {
            List<Double> vector = response.get(i);

            if (vector == null || vector.isEmpty()) {
                throw new IllegalStateException(
                        "TEI вернул пустой вектор с индексом " + i
                );
            }
        }
    }

    private float[] toFloatArray(
            List<Double> vector
    ) {
        float[] result = new float[vector.size()];

        for (int i = 0; i < vector.size(); i++) {
            result[i] = vector.get(i).floatValue();
        }

        return result;
    }

    private String validateText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Embedding text is empty"
            );
        }

        return text.trim();
    }

    private boolean hasE5Prefix(String text) {
        return text.startsWith("query: ")
                || text.startsWith("passage: ");
    }
}