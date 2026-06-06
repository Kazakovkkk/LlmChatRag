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

    /**
     * 1. Гибридный поиск похожих документов для гостя или админа
     */
    @Override
    public void searchSimilar(SearchRequest request, StreamObserver<SearchResponse> responseObserver) {
        long start = System.currentTimeMillis();
        String hotelKey = request.getHotelKey();
        String query = request.getQuery();
        int limit = request.getLimit() > 0 ? request.getLimit() : 5;

        log.info("⏱ gRPC [searchSimilar] начало | Отель: {} | Запрос: '{}'", hotelKey, query);

        try {
            // Вызываем гибридный поиск из сервиса
            List<Document> docs = incidentEmbeddingService.searchSimilarIncidents(hotelKey, query, limit, "hybrid");

            SearchResponse.Builder responseBuilder = SearchResponse.newBuilder();

            docs.forEach(doc -> {
                DocumentProto.Builder docBuilder = DocumentProto.newBuilder()
                        .setId(doc.getId() != null ? doc.getId() : "")
                        .setText(doc.getText() != null ? doc.getText() : "");

                double score = doc.getScore() != null ? doc.getScore() : 0.0;
                docBuilder.setScore(score);

                if (doc.getMetadata() != null) {
                    doc.getMetadata().forEach((k, v) ->
                            docBuilder.putMetadata(k, v != null ? v.toString() : ""));
                }

                responseBuilder.addDocuments(docBuilder.build());
            });

            log.info("⏱ gRPC [searchSimilar] успешно завершен за {} мс | Найдено: {}",
                    System.currentTimeMillis() - start, docs.size());

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Ошибка gRPC [searchSimilar] для отеля {}: {}", hotelKey, e.getMessage());
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Внутренняя ошибка поиска: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * 2. Загрузка и семантическое разбиение PDF документа в фоне
     */
    @Override
    public void uploadDocument(UploadRequest request, StreamObserver<UploadResponse> responseObserver) {
        long start = System.currentTimeMillis();
        String hotelKey = request.getHotelKey();
        String filename = request.getFilename();
        String mode = request.getMode(); 

        log.info("⏱ gRPC [uploadDocument] начало | Отель: {} | Режим: {} | Файл: '{}'", hotelKey, mode, filename);

        try {
            byte[] pdfBytes = request.getPdfData().toByteArray();
            List<String> tags = request.getTagsList().isEmpty()
                    ? List.of(filename.replaceAll("\\.pdf$", "").toLowerCase())
                    : request.getTagsList();

            // 2. ИСПРАВЛЕНИЕ: Передаем mode четвертым аргументом в конвейер векторизации
            incidentEmbeddingService.storePdfDocument(hotelKey, pdfBytes, tags, mode);

            long elapsed = System.currentTimeMillis() - start;
            log.info("⏱ gRPC [uploadDocument] успешно обработан за {} мс для отеля {} в режиме {}", elapsed, hotelKey, mode);

            // Формируем понятный статус-текст для админки
            String actionMessage = "OVERWRITE".equalsIgnoreCase(mode)
                    ? "База знаний полностью перезаписана для отеля "
                    : "Новые данные успешно добавлены в коллекцию отеля ";

            responseObserver.onNext(UploadResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage(actionMessage + hotelKey)
                    .setFilename(filename)
                    .setTag(tags.get(0))
                    .build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Ошибка gRPC [uploadDocument] для отеля {} в режиме {}: {}", hotelKey, mode, e.getMessage());
            responseObserver.onNext(UploadResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Ошибка обработки PDF (" + mode + "): " + e.getMessage())
                    .setFilename(filename)
                    .build());
            responseObserver.onCompleted();
        }
    }

    /**
     * 3. Выгрузка всех текстовых чанков отеля (для админ-панели)
     */
    @Override
    public void getChunks(GetChunksRequest request, StreamObserver<GetChunksResponse> responseObserver) {
        long start = System.currentTimeMillis();
        String hotelKey = request.getHotelKey();
        int limit = request.getLimit() > 0 ? request.getLimit() : 100;

        log.info("⏱ gRPC [getChunks] начало | Отель: {} | Лимит: {}", hotelKey, limit);

        try {
            List<Document> chunks = incidentEmbeddingService.getAllChunks(hotelKey, limit);

            GetChunksResponse.Builder responseBuilder = GetChunksResponse.newBuilder();

            chunks.forEach(c -> {
                DocumentProto.Builder chunkBuilder = DocumentProto.newBuilder()
                        .setId(c.getId() != null ? c.getId() : "")
                        .setText(c.getText() != null ? c.getText() : "")
                        .setScore(0.0);

                if (c.getMetadata() != null) {
                    c.getMetadata().forEach((k, v) ->
                            chunkBuilder.putMetadata(k, v != null ? v.toString() : ""));
                }
                responseBuilder.addChunks(chunkBuilder.build());
            });

            log.info("⏱ gRPC [getChunks] завершен за {} мс | Извлечено чанков: {}",
                    System.currentTimeMillis() - start, chunks.size());

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Ошибка gRPC [getChunks] для отеля {}: {}", hotelKey, e.getMessage());
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Не удалось извлечь чанки СУБД: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * 4. Точечное обновление (мутация) одного вектора и payload-текста в Qdrant
     */
    @Override
    public void updateChunk(UpdateChunkRequest request, StreamObserver<UpdateChunkResponse> responseObserver) {
        long start = System.currentTimeMillis();
        String hotelKey = request.getHotelKey();
        String chunkId = request.getChunkId();
        String newText = request.getNewText();

        log.info("⏱ gRPC [updateChunk] начало | Отель: {} | ID чанка: {}", hotelKey, chunkId);

        try {
            // Вызов мутации dense + sparse пространств
            incidentEmbeddingService.updateChunkText(hotelKey, chunkId, newText);

            log.info("⏱ gRPC [updateChunk] успешно завершен за {} мс", System.currentTimeMillis() - start);

            responseObserver.onNext(UpdateChunkResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Векторный индекс и текстовый payload успешно модифицированы в Qdrant")
                    .build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Ошибка gRPC [updateChunk] для точки {} в отеле {}: {}", chunkId, hotelKey, e.getMessage());
            responseObserver.onNext(UpdateChunkResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Ошибка выполнения точечной мутации: " + e.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }
}