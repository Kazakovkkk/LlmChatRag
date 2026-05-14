package qdrantservice.controller;

import qdrantservice.dto.SearchRequest;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
@Slf4j
public class QdrantController {

    private final IncidentEmbeddingService incidentService;


    @PostMapping("/add")
    public String addIncident(@RequestBody Map<String, Object> request) {
        String text = (String) request.get("text");
        List<String> tags = (List<String>) request.get("tags");
        incidentService.storeIncident(text, tags);
        return "Incident added successfully!";
    }

    @PostMapping(path = "/similar", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Document>> getSimilarIncidents(
            @RequestBody SearchRequest request) {
        try {
            long start = System.currentTimeMillis();

            List<Document> docs = incidentService.searchSimilarIncidents(
                    request.getQuery(),
                    request.getLimit(),
                    request.getSearchType()
            );

            log.info("⏱ Поиск [{}]: {} мс | {} результатов",
                    request.getSearchType(),
                    System.currentTimeMillis() - start,
                    docs.size());

            return ResponseEntity.ok(docs);
        } catch (Exception e) {
            log.error("Ошибка поиска: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/health")
    public String health() {
        return "Qdrant service is running!";
    }

    @PostMapping(value = "/upload-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadPdf(
            @RequestParam("file") MultipartFile file) {
        try {
            String originalFilename = file.getOriginalFilename();
            String tag = originalFilename != null
                    ? originalFilename.replaceAll("\\.pdf$", "").toLowerCase()
                    : "unknown";

            log.info("Загрузка PDF: {}, тег: {}", originalFilename, tag);

            incidentService.storePdfDocumentAsync(file.getBytes(), List.of(tag));

            return ResponseEntity.ok(Map.of(
                    "status", "processing",
                    "file", originalFilename,
                    "tag", tag,
                    "message", "PDF принят в обработку, загрузка идёт в фоне"
            ));

        } catch (Exception e) {
            log.error("Ошибка обработки PDF: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
    @GetMapping("/reports/{filename}")
    public ResponseEntity<byte[]> downloadReport(@PathVariable String filename) {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get("reports", filename);
            if (!java.nio.file.Files.exists(path)) {
                return ResponseEntity.notFound().build();
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(path);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", "application/pdf")
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(value = "/reports", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> listReports() {
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get("reports");
            if (!java.nio.file.Files.exists(dir)) {
                return ResponseEntity.ok("<html><body><h2>Папка reports не найдена</h2></body></html>");
            }

            List<String> files = java.nio.file.Files.list(dir)
                    .map(p -> p.getFileName().toString())
                    .filter(f -> f.endsWith(".pdf"))
                    .sorted()
                    .collect(Collectors.toList());

            StringBuilder html = new StringBuilder();
            html.append("""
            <html>
            <head>
                <meta charset="UTF-8">
                <title>PDF Отчёты</title>
                <style>
                    body { font-family: Arial, sans-serif; padding: 30px; background: #f5f5f5; }
                    h2 { color: #2c3e50; }
                    .file-list { list-style: none; padding: 0; }
                    .file-item {
                        background: white;
                        margin: 10px 0;
                        padding: 15px 20px;
                        border-radius: 8px;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    }
                    .file-name { color: #34495e; font-size: 14px; }
                    .download-btn {
                        background: #3498db;
                        color: white;
                        padding: 8px 16px;
                        border-radius: 5px;
                        text-decoration: none;
                        font-size: 13px;
                    }
                    .download-btn:hover { background: #2980b9; }
                    .empty { color: #999; font-style: italic; }
                </style>
            </head>
            <body>
                <h2>📄 PDF Отчёты по обработке документов</h2>
            """);

            if (files.isEmpty()) {
                html.append("<p class='empty'>Отчёты не найдены. Загрузите PDF документ.</p>");
            } else {
                html.append("<p>Найдено файлов: <b>").append(files.size()).append("</b></p>");
                html.append("<ul class='file-list'>");
                for (String file : files) {
                    String encodedFile = java.net.URLEncoder.encode(file,
                            java.nio.charset.StandardCharsets.UTF_8);
                    html.append("<li class='file-item'>")
                            .append("<span class='file-name'>📎 ").append(file).append("</span>")
                            .append("<a class='download-btn' href='/api/incidents/reports/")
                            .append(encodedFile).append("' download>⬇ Скачать</a>")
                            .append("</li>");
                }
                html.append("</ul>");
            }

            html.append("</body></html>");
            return ResponseEntity.ok(html.toString());

        } catch (Exception e) {
            log.error("Ошибка получения списка отчётов: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("<html><body><h2>Ошибка</h2></body></html>");
        }
    }

}