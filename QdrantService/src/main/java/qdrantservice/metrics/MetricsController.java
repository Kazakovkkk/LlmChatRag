package qdrantservice.metrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;
@RestController
@RequestMapping("/api/incidents/metrics")
@RequiredArgsConstructor
@Slf4j

//GET http://localhost:8085/api/incidents/evaluate/compare?k=5
//GET http://localhost:8085/api/incidents/evaluate?searchType=hybrid&k=5
public class MetricsController {
    private final RetrievalMetricsService metricsService;
    @GetMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate(
            @RequestParam(defaultValue = "vector") String searchType,
            @RequestParam(defaultValue = "5") int k) {
        try {
            log.info("Запуск оценки метрик | searchType: {} | k: {}", searchType, k);
            long start = System.currentTimeMillis();

            Map<String, Object> result = metricsService.evaluate(searchType, k);
            result.put("evaluationTimeMs", System.currentTimeMillis() - start);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Ошибка оценки метрик: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // Сравнение всех трёх методов поиска
    @GetMapping("/evaluate/compare")
    public ResponseEntity<Map<String, Object>> compareAll(
            @RequestParam(defaultValue = "5") int k) {
        try {
            log.info("Запуск сравнения всех методов поиска | k: {}", k);
            long start = System.currentTimeMillis();

            Map<String, Object> vectorMetrics = metricsService.evaluate("vector", k);
            Map<String, Object> keywordMetrics = metricsService.evaluate("keyword", k);
            Map<String, Object> hybridMetrics = metricsService.evaluate("hybrid", k);

            Map<String, Object> vectorResult = new LinkedHashMap<>();
            vectorResult.put("metrics", vectorMetrics.get("metrics"));
            vectorResult.put("searchTime", vectorMetrics.get("searchTime"));

            Map<String, Object> keywordResult = new LinkedHashMap<>();
            keywordResult.put("metrics", keywordMetrics.get("metrics"));
            keywordResult.put("searchTime", keywordMetrics.get("searchTime"));

            Map<String, Object> hybridResult = new LinkedHashMap<>();
            hybridResult.put("metrics", hybridMetrics.get("metrics"));
            hybridResult.put("searchTime", hybridMetrics.get("searchTime"));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("k", k);
            result.put("vector", vectorResult);
            result.put("keyword", keywordResult);
            result.put("hybrid", hybridResult);
            result.put("totalEvaluationTimeMs", System.currentTimeMillis() - start);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Ошибка сравнения: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
