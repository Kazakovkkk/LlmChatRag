package LlmChatRag.grpc;

import com.example.grpc.*;
import LlmChatRag.dto.DocumentDto;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@Slf4j
@Service
public class RagGrpcClient {

    private final RagServiceGrpc.RagServiceBlockingStub stub;

    public RagGrpcClient(GrpcChannelFactory channelFactory) {
        ManagedChannel channel = channelFactory.createChannel("rag-service");
        this.stub = RagServiceGrpc.newBlockingStub(channel);
    }

    public List<DocumentDto> searchSimilar(String query, int limit) {
        SearchRequest request = SearchRequest.newBuilder()
                .setQuery(query)
                .setLimit(limit)
                .build();

        SearchResponse response = stub.searchSimilar(request);

        return response.getDocumentsList().stream()
                .map(this::toDocumentDto)
                .collect(Collectors.toList());
    }

    private DocumentDto toDocumentDto(DocumentProto proto) {
        DocumentDto dto = new DocumentDto();
        dto.setId(proto.getId());
        dto.setText(proto.getText());
        dto.setScore(proto.getScore());
        return dto;
    }
    // grpc/RagGrpcClient.java — добавляем метод uploadDocument
    public Map<String, Object> uploadDocument(byte[] pdfBytes, String filename) {
        long start = System.currentTimeMillis();
        log.info("⏱ gRPC upload начало | файл: '{}'", filename);

        UploadRequest request = UploadRequest.newBuilder()
                .setPdfData(com.google.protobuf.ByteString.copyFrom(pdfBytes))
                .setFilename(filename)
                .build();

        UploadResponse response = stub.uploadDocument(request);

        long elapsed = System.currentTimeMillis() - start;
        log.info("⏱ gRPC upload завершён | {} мс | файл: '{}'", elapsed, filename);

        return Map.of(
                "status", response.getSuccess() ? "success" : "error",
                "message", response.getMessage(),
                "file", response.getFilename(),
                "tag", response.getTag(),
                "transferTimeMs", elapsed
        );
    }
}