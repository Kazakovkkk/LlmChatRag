package LlmChatRag.Controller;

import LlmChatRag.service.PreprocessingEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class MetricsController {
    private final PreprocessingEvaluationService preprocessingEvaluationService;
    @GetMapping("/api/evaluate/preprocessing")
    public ResponseEntity<Map<String, Object>> evaluatePreprocessing(
            @RequestParam(defaultValue = "5") int k) {
        try {
            Map<String, Object> report = preprocessingEvaluationService.evaluatePreprocessingLift(k);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
