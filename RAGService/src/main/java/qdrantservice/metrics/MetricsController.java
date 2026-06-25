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
public class MetricsController {

    private final RetrievalMetricsService metricsService;

    // Тестирование одного конкретного метода поиска для указанного отеля
    // Пример: GET http://localhost:8085/api/incidents/metrics/evaluate?hotelKey=cosmos&searchType=hybrid&k=5
    @GetMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate(
            @RequestParam String hotelKey, // <-- ДОБАВЛЕНО: Ключ тестируемого отеля
            @RequestParam(defaultValue = "vector") String searchType,
            @RequestParam(defaultValue = "5") int k) {
        try {
            log.info("Запуск оценки метрик | Отель: {} | searchType: {} | k: {}", hotelKey, searchType, k);
            long start = System.currentTimeMillis();

            // Передаем hotelKey в сервис вычисления метрик
            Map<String, Object> result = metricsService.evaluate(hotelKey, searchType, k);
            result.put("evaluationTimeMs", System.currentTimeMillis() - start);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Ошибка оценки метрик для отеля {}: {}", hotelKey, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // Сравнение всех трёх методов поиска для указанного отеля (Твоя главная киллер-фича для графиков в ВКР)
    // Пример: GET http://localhost:8085/api/incidents/metrics/evaluate/compare?hotelKey=cosmos&k=5
    @GetMapping("/evaluate/compare")
    public ResponseEntity<Map<String, Object>> compareAll(
            @RequestParam String hotelKey, // <-- ДОБАВЛЕНО: Ключ тестируемого отеля
            @RequestParam(defaultValue = "5") int k) {
        try {
            log.info("Запуск бенчмарка сравнения всех алгоритмов | Отель: {} | k: {}", hotelKey, k);
            long start = System.currentTimeMillis();

            // Передаем hotelKey во все три замера
            Map<String, Object> vectorMetrics = metricsService.evaluate(hotelKey, "vector", k);
            Map<String, Object> keywordMetrics = metricsService.evaluate(hotelKey, "keyword", k);
            Map<String, Object> hybridMetrics = metricsService.evaluate(hotelKey, "hybrid", k);

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
            result.put("hotelKey", hotelKey);
            result.put("k", k);
            result.put("vector", vectorResult);
            result.put("keyword", keywordResult);
            result.put("hybrid", hybridResult);
            result.put("totalEvaluationTimeMs", System.currentTimeMillis() - start);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Ошибка сравнительного бенчмарка для отеля {}: {}", hotelKey, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}