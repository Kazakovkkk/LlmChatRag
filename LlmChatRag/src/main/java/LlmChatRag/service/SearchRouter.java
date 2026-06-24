package LlmChatRag.service;

import LlmChatRag.dto.DocumentDto;
import LlmChatRag.dto.SearchRequest;
import LlmChatRag.grpc.RagGrpcClient;
import com.example.grpc.DocumentProto;
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

    public SearchRouter(@Qualifier("qdrantRestClient") RestClient qdrantRestClient, RagGrpcClient ragGrpcClient) {
        this.qdrantRestClient = qdrantRestClient;
        this.ragGrpcClient = ragGrpcClient;
    }

    public List<DocumentDto> search(String hotelKey, String query, int limit) {
        //log.info("⏱ Маршрутизация поиска RAG [Hotel: {}, Protocol: {}] | Запрос: '{}'", hotelKey, protocol.toUpperCase(), query);
        return "rest".equalsIgnoreCase(protocol) ? searchViaRest(hotelKey, query, limit) : searchViaGrpc(hotelKey, query, limit);
    }

    private List<DocumentDto> searchViaGrpc(String hotelKey, String query, int limit) {
        return ragGrpcClient.searchSimilar(hotelKey, query, limit);
    }
    public List<DocumentDto> searchBatch(
            String hotelKey,
            List<String> queries,
            int limit
    ) {
        return "grpc".equalsIgnoreCase(protocol)
                ? ragGrpcClient.searchSimilarBatch(hotelKey, queries, limit)
                : searchBatchViaRest(hotelKey, queries, limit);
    }
    private List<DocumentDto> searchBatchViaRest(
            String hotelKey,
            List<String> queries,
            int limit
    ) {
        List<Map<String, Object>> rawResult = qdrantRestClient.post()
                .uri("/api/incidents/similar/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "hotelKey", hotelKey,
                        "queries", queries,
                        "limit", limit
                ))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (rawResult == null) {
            return List.of();
        }

        return rawResult.stream().map(raw -> {
            DocumentDto dto = new DocumentDto();
            dto.setId((String) raw.get("id"));

            Object text = raw.get("text");
            if (text == null) text = raw.get("content");
            dto.setText(text != null ? text.toString() : "");

            Object score = raw.get("score");
            dto.setScore(score instanceof Number number
                    ? number.doubleValue()
                    : 0.0);

            return dto;
        }).toList();
    }
    private List<DocumentDto> searchViaRest(String hotelKey, String query, int limit) {
        SearchRequest payload = new SearchRequest(query, limit);
        payload.setSearchType("hybrid");

        List<Map<String, Object>> rawResult = qdrantRestClient.post()
                .uri("/api/incidents/similar")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("hotelKey", hotelKey, "query", query, "limit", limit, "searchType", "hybrid"))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (rawResult == null) return List.of();

        return rawResult.stream().map(raw -> {
            DocumentDto dto = new DocumentDto();
            dto.setId((String) raw.get("id"));

            // 1. РЕЗИЛЬЕНТНОЕ ИЗВЛЕЧЕНИЕ ТЕКСТА (Проверяем все возможные ключи Spring AI)
            String extractedText = null;
            if (raw.containsKey("text") && raw.get("text") != null) {
                extractedText = (String) raw.get("text");
            } else if (raw.containsKey("content") && raw.get("content") != null) {
                extractedText = (String) raw.get("content");
            }

            // Жесткая защита от NullPointerException: если текст не найден, пишем пустую строку ""
            dto.setText(extractedText != null ? extractedText : "");

            // 2. ИЗВЛЕЧЕНИЕ SCORE (С защитой от разных вариантов размещения)
            Map<String, Object> metadata = (Map<String, Object>) raw.get("metadata");
            if (metadata != null && metadata.containsKey("score")) {
                dto.setScore(((Number) metadata.get("score")).doubleValue());
            } else if (raw.containsKey("score") && raw.get("score") != null) {
                dto.setScore(((Number) raw.get("score")).doubleValue());
            } else {
                dto.setScore(0.0);
            }

            return dto;
        }).collect(Collectors.toList());
    }
    private DocumentDto toDocumentDto(DocumentProto proto) {
        DocumentDto dto = new DocumentDto();
        dto.setId(proto.getId());
        dto.setText(proto.getText());
        dto.setScore(proto.getScore());
        return dto;
    }
    public String getProtocol() { return protocol; }
}