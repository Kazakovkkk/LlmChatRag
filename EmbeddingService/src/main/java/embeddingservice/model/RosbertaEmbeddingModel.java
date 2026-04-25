package embeddingservice.model;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Qualifier;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RosbertaEmbeddingModel extends AbstractEmbeddingModel {
    RestClient restClient;
    public RosbertaEmbeddingModel(@Qualifier("ruEnHuggingFaceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }
    @Override
    public @NotNull EmbeddingResponse call(@NotNull EmbeddingRequest request) {
        long start = System.currentTimeMillis();
        String text = request.getInstructions().get(0);
        String formatted = "query: " + text;

        var payload = Map.of("inputs", formatted);

        List<List<Double>> response = restClient.post()
                .uri("/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        List<Double> vector = response.get(0);

        float[] embedding = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            embedding[i] = vector.get(i).floatValue();
        }
        log.info("gRPC Model embed | {} мс", System.currentTimeMillis() - start);
        return new EmbeddingResponse(
                List.of(new Embedding(embedding, 0))
        );
    }

    @Override
    public @NotNull float[] embed(@NotNull Document document) {
        return call(new EmbeddingRequest(List.of(document.getFormattedContent()), null))
                .getResults().getFirst().getOutput();
    }
}