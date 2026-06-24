package qdrantservice.controller;

import qdrantservice.dto.BatchSearchRequest;
import qdrantservice.dto.SearchRequest;
import qdrantservice.dto.StoreRequest;
import qdrantservice.service.IncidentEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
@Slf4j
public class QdrantController {

    private final IncidentEmbeddingService incidentService;

    @PostMapping("/add")
    public String addIncident(@RequestBody StoreRequest request) {
        incidentService.storeIncident(request.getHotelKey(), request.getText(), request.getTags());
        return "Incident added successfully for hotel: " + request.getHotelKey();
    }

    @PostMapping(path = "/similar", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Document>> getSimilarIncidents(@RequestBody SearchRequest request) {
        try {
            long start = System.currentTimeMillis();

            List<Document> docs = incidentService.searchSimilarIncidents(
                    request.getHotelKey(),
                    request.getQuery(),
                    request.getLimit(),
                    request.getSearchType()
            );

            log.info("⏱ Поиск [{} | Hotel: {}]: {} мс | {} результатов",
                    request.getSearchType(), request.getHotelKey(),
                    System.currentTimeMillis() - start, docs.size());

            return ResponseEntity.ok(docs);
        } catch (Exception e) {
            log.error("Ошибка поиска для отеля {}: {}", request.getHotelKey(), e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    @PostMapping(
            path = "/similar/batch",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<Document>> getSimilarIncidentsBatch(
            @RequestBody BatchSearchRequest request
    ) {
        if (request.hotelKey() == null ||
                request.hotelKey().isBlank() ||
                request.queries() == null ||
                request.queries().isEmpty()) {

            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(
                incidentService.searchSimilarIncidentsBatch(
                        request.hotelKey(),
                        request.queries(),
                        request.limit()
                )
        );
    }
// Обновленный метод в файле QdrantController.java

    @PostMapping(value = "/upload-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("hotelKey") String hotelKey,
            @RequestParam(value = "mode", defaultValue = "APPEND") String mode) {
        try {
            String originalFilename = file.getOriginalFilename();
            String tag = originalFilename != null
                    ? originalFilename.replaceAll("\\.pdf$", "").toLowerCase()
                    : "unknown";

            log.info("Загрузка PDF для отеля: {}, Режим: {}, Файл: {}, Тег: {}", hotelKey, mode, originalFilename, tag);

            // Передаем mode в асинхронный метод
            incidentService.storePdfDocumentAsync(hotelKey, file.getBytes(), List.of(tag), mode);

            return ResponseEntity.ok(Map.of(
                    "status", "processing",
                    "hotelKey", hotelKey,
                    "mode", mode,
                    "file", originalFilename,
                    "message", "PDF принят в обработку в режиме " + mode + ", развертывание коллекции и загрузка идут в фоне"
            ));
        } catch (Exception e) {
            log.error("Ошибка обработки PDF в режиме {}: {}", mode, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}