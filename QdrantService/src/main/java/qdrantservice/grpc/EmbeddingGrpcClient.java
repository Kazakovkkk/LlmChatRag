package qdrantservice.grpc;

import com.example.grpc.embedding.EmbedBatchRequest;
import com.example.grpc.embedding.EmbedBatchResponse;
import com.example.grpc.embedding.EmbedRequest;
import com.example.grpc.embedding.EmbeddingServiceGrpc;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class EmbeddingGrpcClient {

    private final EmbeddingServiceGrpc.EmbeddingServiceBlockingStub stub;

    public EmbeddingGrpcClient(GrpcChannelFactory channelFactory) {
        ManagedChannel channel = channelFactory.createChannel("embedding-service");
        this.stub = EmbeddingServiceGrpc.newBlockingStub(channel);
    }

    public float[] embed(String text) {
        long start = System.currentTimeMillis();

        EmbedRequest request = EmbedRequest.newBuilder()
                .setText(text)
                .build();

        var response = stub.embed(request);
        float[] vector = toFloatArray(response.getVectorList());

        log.info("gRPC embed | {} мс", System.currentTimeMillis() - start);
        return vector;
    }

    public List<float[]> embedBatch(List<String> texts) {
        long start = System.currentTimeMillis();

        EmbedBatchRequest request = EmbedBatchRequest.newBuilder()
                .addAllTexts(texts)
                .build();

        EmbedBatchResponse response = stub.embedBatch(request);

        List<float[]> result = response.getEmbeddingsList().stream()
                .map(e -> toFloatArray(e.getVectorList()))
                .toList();

        log.info("gRPC embedBatch | {} текстов | {} мс",
                texts.size(), System.currentTimeMillis() - start);
        return result;
    }

    private float[] toFloatArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }
}