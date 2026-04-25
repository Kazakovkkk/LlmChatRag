package qdrantservice.grpc;

import com.example.grpc.*;
import qdrantservice.service.IncidentEmbeddingService;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.ai.document.Document;

import java.util.List;
@Slf4j
@GrpcService
public class RagGrpcService extends RagServiceGrpc.RagServiceImplBase {

    private final IncidentEmbeddingService incidentEmbeddingService;

    public RagGrpcService(IncidentEmbeddingService incidentEmbeddingService) {
        this.incidentEmbeddingService = incidentEmbeddingService;
    }

    @Override
    public void searchSimilar(SearchRequest request,
                              StreamObserver<SearchResponse> responseObserver) {
        long start = System.currentTimeMillis();
        log.info("⏱ gRPC searchSimilar начало | запрос: '{}'", request.getQuery());

        List<Document> docs = incidentEmbeddingService
                .searchSimilarIncidents(request.getQuery(), request.getLimit());
        log.info("⏱ gRPC searchSimilar: {} мс | найдено: {} документов",
                System.currentTimeMillis() - start, docs.size());

        SearchResponse.Builder responseBuilder = SearchResponse.newBuilder();

        docs.forEach(doc -> {
            DocumentProto.Builder docBuilder = DocumentProto.newBuilder()
                    .setId(doc.getId())
                    .setText(doc.getText());

            // ← достаём score из metadata если doc.getScore() null
            double score = 0.0;
            if (doc.getScore() != null) {
                score = doc.getScore();
            } else if (doc.getMetadata() != null
                    && doc.getMetadata().get("score") != null) {
                score = ((Number) doc.getMetadata().get("score")).doubleValue();
            }
            docBuilder.setScore(score);

            if (doc.getMetadata() != null) {
                doc.getMetadata().forEach((k, v) ->
                        docBuilder.putMetadata(k, v != null ? v.toString() : ""));
            }

            responseBuilder.addDocuments(docBuilder.build());
        });

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }
    @Override
    public void uploadDocument(UploadRequest request,
                               StreamObserver<UploadResponse> responseObserver) {
        long start = System.currentTimeMillis();
        log.info("⏱ gRPC uploadDocument начало | файл: '{}'", request.getFilename());

        try {
            byte[] pdfBytes = request.getPdfData().toByteArray();
            String filename = request.getFilename();
            List<String> tags = request.getTagsList().isEmpty()
                    ? List.of(filename.replaceAll("\\.pdf$", "").toLowerCase())
                    : request.getTagsList();

            incidentEmbeddingService.storePdfDocument(pdfBytes, tags);

            long elapsed = System.currentTimeMillis() - start;
            log.info("⏱ gRPC uploadDocument завершён | {} мс | файл: '{}'", elapsed, filename);

            responseObserver.onNext(UploadResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("PDF успешно обработан за " + elapsed + " мс")
                    .setFilename(filename)
                    .setTag(tags.get(0))
                    .build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("⏱ gRPC uploadDocument ошибка | {} мс | {}", elapsed, e.getMessage());

            responseObserver.onNext(UploadResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }
}