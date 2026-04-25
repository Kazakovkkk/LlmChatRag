package qdrantservice.service;

import qdrantservice.grpc.EmbeddingGrpcClient;
import qdrantservice.model.RemoteEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class EmbeddingRouter {

    private final RemoteEmbeddingModel restClient;
    private final EmbeddingGrpcClient grpcClient;
    private final String protocol;

    public EmbeddingRouter(
            RemoteEmbeddingModel restClient,
            EmbeddingGrpcClient grpcClient,
            @Value("${embedding.protocol:rest}") String protocol) {
        this.restClient = restClient;
        this.grpcClient = grpcClient;
        this.protocol = protocol;
        log.info("Embedding protocol: {}", protocol);
    }

    public float[] embed(String text) {
        long start = System.currentTimeMillis();
        float[] result = switch (protocol) {
            case "grpc" -> grpcClient.embed(text);
            default -> restClient.embed(text);
        };
        log.info("=== Embed | Protocol: {} | {} мс ===",
                protocol, System.currentTimeMillis() - start);
        return result;
    }

    public List<float[]> embedBatch(List<String> texts) {
        long start = System.currentTimeMillis();
        List<float[]> result = switch (protocol) {
            case "grpc" -> grpcClient.embedBatch(texts);
            default -> texts.stream().map(restClient::embed).toList();
        };
        log.info("=== EmbedBatch | Protocol: {} | {} текстов | {} мс ===",
                protocol, texts.size(), System.currentTimeMillis() - start);
        return result;
    }
}