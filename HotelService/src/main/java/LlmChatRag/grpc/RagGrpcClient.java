package LlmChatRag.grpc;

import com.example.grpc.*;
import LlmChatRag.dto.DocumentDto;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagGrpcClient {

    private final RagServiceGrpc.RagServiceBlockingStub stub;

    public RagGrpcClient(GrpcChannelFactory channelFactory) {
        ManagedChannel channel = channelFactory.createChannel("rag-service");
        this.stub = RagServiceGrpc.newBlockingStub(channel);
    }

    public List<DocumentDto> searchSimilar(String hotelKey, String query, int limit) {

        SearchRequest request = SearchRequest.newBuilder()
                .setHotelKey(hotelKey)
                .setQuery(query)
                .setLimit(limit)
                .build();

        SearchResponse response = stub.searchSimilar(request);

        return response.getDocumentsList().stream()
                .map(this::toDocumentDto)
                .collect(Collectors.toList());
    }
    public List<DocumentDto> searchSimilarBatch(
            String hotelKey,
            List<String> queries,
            int limit
    ) {
        BatchSearchRequest request = BatchSearchRequest.newBuilder()
                .setHotelKey(hotelKey)
                .addAllQueries(queries)
                .setLimit(limit)
                .build();

        SearchResponse response = stub
                .withDeadlineAfter(6, TimeUnit.SECONDS)
                .searchSimilarBatch(request);

        return response.getDocumentsList().stream()
                .map(this::toDocumentDto)
                .toList();
    }

    private DocumentDto toDocumentDto(DocumentProto proto) {
        DocumentDto dto = new DocumentDto();
        dto.setId(proto.getId());
        dto.setText(proto.getText());
        dto.setScore(proto.getScore());
        return dto;
    }
}