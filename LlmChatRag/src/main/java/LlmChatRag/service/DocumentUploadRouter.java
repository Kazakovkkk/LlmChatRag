// service/DocumentUploadRouter.java
package LlmChatRag.service;

import LlmChatRag.grpc.RagGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Service
public class DocumentUploadRouter {

    private final RestClient qdrantRestClient;
    private final RagGrpcClient ragGrpcClient;

    @Value("${document.upload.protocol:rest}")
    private String protocol;

    public DocumentUploadRouter(
            @Qualifier("qdrantRestClient") RestClient qdrantRestClient,
            RagGrpcClient ragGrpcClient) {
        this.qdrantRestClient = qdrantRestClient;
        this.ragGrpcClient = ragGrpcClient;
    }

    public Map upload(byte[] fileBytes, String filename) {
        log.info("⏱ Загрузка документа | Protocol: {} | файл: '{}'", protocol, filename);
        long start = System.currentTimeMillis();

        Map result = switch (protocol) {
            case "grpc" -> uploadViaGrpc(fileBytes, filename);
            default -> uploadViaRest(fileBytes, filename);
        };

        log.info("⏱ Загрузка завершена | Protocol: {} | {} мс | файл: '{}'",
                protocol, System.currentTimeMillis() - start, filename);

        return result;
    }

    private Map uploadViaGrpc(byte[] fileBytes, String filename) {
        log.info("→ gRPC upload | '{}'", filename);
        return ragGrpcClient.uploadDocument(fileBytes, filename);
    }

    private Map uploadViaRest(byte[] fileBytes, String filename) {
        log.info("→ REST upload | '{}'", filename);
        long start = System.currentTimeMillis();

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() { return filename; }
        }).contentType(MediaType.APPLICATION_PDF);

        Map result = qdrantRestClient.post()
                .uri("/api/incidents/upload-pdf")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .body(Map.class);

        log.info("⏱ REST upload завершён | {} мс", System.currentTimeMillis() - start);
        return result;
    }
}