// service/SearchRouter.java
package LlmChatRag.service;

import LlmChatRag.dto.DocumentDto;
import LlmChatRag.dto.SearchRequest;
import LlmChatRag.grpc.RagGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SearchRouter {

    private final RestClient qdrantRestClient;
    private final RagGrpcClient ragGrpcClient;

    @Value("${search.protocol:grpc}")
    private String protocol;

    public SearchRouter(
            @Qualifier("qdrantRestClient") RestClient qdrantRestClient,
            RagGrpcClient ragGrpcClient) {
        this.qdrantRestClient = qdrantRestClient;
        this.ragGrpcClient = ragGrpcClient;
    }

    public List<DocumentDto> search(String query, int limit) {
        log.info("⏱ Поиск документов | Protocol: {} | запрос: '{}'", protocol, query);
        long start = System.currentTimeMillis();

        List<DocumentDto> result = switch (protocol) {
            case "rest" -> searchViaRest(query, limit);
            default -> searchViaGrpc(query, limit);
        };

        log.info("⏱ Поиск завершён | Protocol: {} | {} мс | найдено: {} документов",
                protocol, System.currentTimeMillis() - start, result.size());

        return result;
    }

    private List<DocumentDto> searchViaGrpc(String query, int limit) {
        log.info("→ gRPC search | запрос: '{}'", query);
        long start = System.currentTimeMillis();

        List<DocumentDto> result = ragGrpcClient.searchSimilar(query, limit);

        log.info("⏱ gRPC search завершён | {} мс", System.currentTimeMillis() - start);
        return result;
    }

    private List<DocumentDto> searchViaRest(String query, int limit) {
        log.info("→ REST search | запрос: '{}'", query);
        long start = System.currentTimeMillis();

        List<Map<String, Object>> rawResult = qdrantRestClient.post()
                .uri("/api/incidents/similar")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SearchRequest(query, limit))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        log.info("⏱ REST search завершён | {} мс", System.currentTimeMillis() - start);

        if (rawResult == null) return List.of();
        //log.info("REST raw ответ первого документа: {}", rawResult.get(0));
        return rawResult.stream()
                .map(raw -> {
                    DocumentDto dto = new DocumentDto();
                    dto.setId((String) raw.get("id"));
                    dto.setText((String) raw.get("text"));
                    // ← берём score из корня объекта
                    if (raw.get("score") != null) {
                        dto.setScore(((Number) raw.get("score")).doubleValue());
                    } else {
                        dto.setScore(0.0);
                    }

                    dto.setMetadata((Map<String, Object>) raw.get("metadata"));
                    if (dto.getMetadata().get("score") != null){
                        dto.setScore((Double) dto.getMetadata().get("score"));
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }
    public String getProtocol() {
        return protocol;
    }
}