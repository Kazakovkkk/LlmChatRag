package qdrantservice.model;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class RemoteEmbeddingModel extends AbstractEmbeddingModel {

    private final RestClient embeddingRestClient;

    public RemoteEmbeddingModel(@Qualifier("embeddingRestClient") RestClient embeddingRestClient) {
        this.embeddingRestClient = embeddingRestClient;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        String text = request.getInstructions().get(0);
        float[] vector = embeddingRestClient.post()
                .uri("/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", text))
                .retrieve()
                .body(float[].class);

        return new EmbeddingResponse(List.of(new Embedding(vector, 0)));
    }

    @Override
    public float[] embed(Document document) {
        return call(new EmbeddingRequest(
                List.of(document.getFormattedContent()), null))
                .getResults().getFirst().getOutput();
    }
}