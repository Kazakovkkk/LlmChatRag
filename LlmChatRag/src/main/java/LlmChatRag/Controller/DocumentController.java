package LlmChatRag.Controller;

import LlmChatRag.service.DocumentUploadRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentUploadRouter uploadRouter;

    public DocumentController(DocumentUploadRouter uploadRouter) {
        this.uploadRouter = uploadRouter;
    }

    @PostMapping(value = "/upload-pdf",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map> uploadPdf(@RequestParam("file") MultipartFile file) {
        try {
            log.info("Получен запрос на загрузку: {}", file.getOriginalFilename());

            Map response = uploadRouter.upload(
                    file.getBytes(),
                    file.getOriginalFilename()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Ошибка загрузки PDF: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", e.getMessage()));
        }
    }
}