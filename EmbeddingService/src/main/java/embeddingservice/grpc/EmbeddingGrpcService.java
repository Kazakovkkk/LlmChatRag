package embeddingservice.grpc;

import com.example.grpc.embedding.EmbedBatchRequest;
import com.example.grpc.embedding.EmbedBatchResponse;
import com.example.grpc.embedding.EmbedRequest;
import com.example.grpc.embedding.EmbedResponse;
import com.example.grpc.embedding.EmbeddingServiceGrpc;
import embeddingservice.model.RosbertaEmbeddingModel;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
public class EmbeddingGrpcService extends EmbeddingServiceGrpc.EmbeddingServiceImplBase {

    private final RosbertaEmbeddingModel embeddingModel;

    public EmbeddingGrpcService(RosbertaEmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void embed(EmbedRequest request,
                      StreamObserver<EmbedResponse> responseObserver) {
        long start = System.currentTimeMillis();

        float[] vector = embeddingModel.embed(new Document(request.getText()));

        EmbedResponse.Builder builder = EmbedResponse.newBuilder();
        for (float v : vector) builder.addVector(v);
        log.info("gRPC embed | {} мс", System.currentTimeMillis() - start);
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();

    }

    @Override
    public void embedBatch(EmbedBatchRequest request,
                           StreamObserver<EmbedBatchResponse> responseObserver) {
        long start = System.currentTimeMillis();

        EmbedBatchResponse.Builder batchBuilder = EmbedBatchResponse.newBuilder();

        request.getTextsList().forEach(text -> {
            float[] vector = embeddingModel.embed(new Document(text));
            EmbedResponse.Builder embBuilder = EmbedResponse.newBuilder();
            for (float v : vector) embBuilder.addVector(v);
            batchBuilder.addEmbeddings(embBuilder.build());
        });

        responseObserver.onNext(batchBuilder.build());
        responseObserver.onCompleted();

        log.info("gRPC embedBatch | {} текстов | {} мс",
                request.getTextsCount(), System.currentTimeMillis() - start);
    }
}