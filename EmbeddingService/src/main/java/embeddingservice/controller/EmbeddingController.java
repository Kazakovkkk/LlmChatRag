package embeddingservice.controller;

import embeddingservice.dto.EmbedBatchRequest;
import embeddingservice.dto.EmbedRequest;
import embeddingservice.model.RosbertaEmbeddingModel;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/embed")
@RequiredArgsConstructor
public class EmbeddingController {

    private final RosbertaEmbeddingModel embeddingModel;

    // Одиночный эмбеддинг
    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public float[] embed(@RequestBody EmbedRequest request) {
        return embeddingModel.embed(new Document(request.getText()));
    }

    // Батч-эмбеддинг
    @PostMapping("/batch")
    public List<float[]> embedBatch(@RequestBody EmbedBatchRequest request) {
        return request.getTexts().stream()
                .map(text -> embeddingModel.embed(new Document(text)))
                .toList();
    }

    @GetMapping("/health")
    public String health() {
        return "Embedding service is running!";
    }
}