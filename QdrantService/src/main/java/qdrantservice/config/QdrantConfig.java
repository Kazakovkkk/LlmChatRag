package qdrantservice.config;

import qdrantservice.model.RemoteEmbeddingModel;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;


import java.util.concurrent.ExecutionException;

@Slf4j
@Configuration
public class QdrantConfig {

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6334}")
    private int qdrantPort;

    @Value("${qdrant.collection-name:incidents}")
    private String collectionName;

    private static final String BM25_STATS_COLLECTION = "bm25_stats";

    @Bean
    @Primary
    public QdrantClient qdrantClient() {
        return new QdrantClient(
                QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, false).build()
        );
    }

    @Bean
    @Primary
    public QdrantVectorStore qdrantVectorStore(QdrantClient qdrantClient,
                                               RemoteEmbeddingModel remoteEmbeddingModel) {
        createCollectionIfNotExists(qdrantClient);
        createBm25StatsCollectionIfNotExists(qdrantClient);

        return QdrantVectorStore.builder(qdrantClient, remoteEmbeddingModel)
                .collectionName(collectionName)
                .initializeSchema(false)
                .build();
    }

    private void createCollectionIfNotExists(QdrantClient qdrantClient) {
        try {
            boolean exists = qdrantClient.listCollectionsAsync().get()
                    .stream()
                    .anyMatch(c -> c.equals(collectionName));

            if (exists) {
                log.info("Коллекция '{}' уже существует", collectionName);
                return;
            }

            log.info("Создаём коллекцию '{}'", collectionName);

            qdrantClient.createCollectionAsync(
                    CreateCollection.newBuilder()
                            .setCollectionName(collectionName)
                            .setVectorsConfig(VectorsConfig.newBuilder()
                                    .setParams(VectorParams.newBuilder()
                                            .setSize(768)
                                            .setDistance(Distance.Cosine)
                                            .build())
                                    .build())
                            .setSparseVectorsConfig(
                                    SparseVectorConfig.newBuilder()
                                            .putMap("sparse", SparseVectorParams.newBuilder()
                                                    .setIndex(SparseIndexConfig.newBuilder()
                                                            .setFullScanThreshold(5000)
                                                            .build())
                                                    .build())
                                            .build())
                            .build()
            ).get();

            log.info("Коллекция '{}' успешно создана", collectionName);

        } catch (ExecutionException | InterruptedException e) {
            log.error("Ошибка создания коллекции: {}", e.getMessage());
            throw new RuntimeException("Не удалось создать коллекцию Qdrant", e);
        }
    }

    // ← Новый метод для bm25_stats
    private void createBm25StatsCollectionIfNotExists(QdrantClient qdrantClient) {
        try {
            boolean exists = qdrantClient.listCollectionsAsync().get()
                    .stream()
                    .anyMatch(c -> c.equals(BM25_STATS_COLLECTION));

            if (exists) {
                log.info("Коллекция '{}' уже существует", BM25_STATS_COLLECTION);
                return;
            }

            log.info("Создаём коллекцию '{}'", BM25_STATS_COLLECTION);

            // Минимальный вектор размером 1 — нам нужен только payload
            qdrantClient.createCollectionAsync(
                    CreateCollection.newBuilder()
                            .setCollectionName(BM25_STATS_COLLECTION)
                            .setVectorsConfig(VectorsConfig.newBuilder()
                                    .setParams(VectorParams.newBuilder()
                                            .setSize(1)
                                            .setDistance(Distance.Cosine)
                                            .build())
                                    .build())
                            .build()
            ).get();

            log.info("Коллекция '{}' успешно создана", BM25_STATS_COLLECTION);

        } catch (ExecutionException | InterruptedException e) {
            log.error("Ошибка создания bm25_stats коллекции: {}", e.getMessage());
            throw new RuntimeException("Не удалось создать коллекцию bm25_stats", e);
        }
    }
}