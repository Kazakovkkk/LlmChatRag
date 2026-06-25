package embeddingservice.controller;

import embeddingservice.dto.EmbedBatchRequest;
import embeddingservice.dto.EmbedRequest;
import embeddingservice.model.RosbertaEmbeddingModel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
//docker run -e CUDA_VISIBLE_DEVICES="" semitechnologies/transformers-inference:intfloat-multilingual-e5-large
@RestController
@RequestMapping("/embed")
@RequiredArgsConstructor
public class EmbeddingController {

    private final RosbertaEmbeddingModel embeddingModel;

    @PostMapping(
            consumes = {
                    MediaType.APPLICATION_JSON_VALUE,
                    MediaType.TEXT_PLAIN_VALUE
            }
    )
    public float[] embed(
            @RequestBody EmbedRequest request
    ) {
        return embeddingModel.embedOne(
                request.getText(),
                request.getInputType()
        );
    }

    @PostMapping("/batch")
    public List<float[]> embedBatch(
            @RequestBody EmbedBatchRequest request
    ) {
        return embeddingModel.embedBatch(
                request.getTexts(),
                request.getInputType()
        );
    }

    @GetMapping("/health")
    public String health() {
        return "Embedding service is running!";
    }
}