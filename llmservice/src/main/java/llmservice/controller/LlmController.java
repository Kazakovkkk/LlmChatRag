package llmservice.controller;

import llmservice.dto.AnswerRequest;
import llmservice.dto.PreprocessedQuestion;
import llmservice.dto.PreprocessRequest;
import llmservice.service.AnswerGenerationService;
import llmservice.service.QueryPreprocessorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
//ollama run llama3:8b --keepalive 24h
@RestController
@RequestMapping("/llm")
@RequiredArgsConstructor
public class LlmController {

    private final QueryPreprocessorService preprocessorService;
    private final AnswerGenerationService answerService;
    private final ObjectMapper objectMapper;

    @PostMapping("/preprocess")
    public Mono<PreprocessedQuestion> preprocess(@RequestBody PreprocessRequest request) {
        return preprocessorService.preprocessQuestion(
                request.getQuestion(),
                request.getHistory() != null ? request.getHistory() : List.of()
        );
    }

    // ← SSE эндпоинт — возвращает Flux<String>
    @PostMapping(value = "/answer/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> answerStream(@RequestBody AnswerRequest request) {
        return answerService.generateAnswerStream(
                request.getQuestion(),
                request.getContext(),
                request.getHistory(),
                request.getTimestamp()
        ).map(token -> {
            try {
                // Оборачиваем в JSON чтобы пробелы не потерялись
                return objectMapper.writeValueAsString(Map.of("token", token));
            } catch (Exception e) {
                return "{\"token\":\"\"}";
            }
        });
    }

    // Оставим обычный эндпоинт тоже
    @PostMapping("/answer")
    public String answer(@RequestBody AnswerRequest request) {
        return answerService.generateAnswer(
                request.getQuestion(),
                request.getContext()
        ).block();
    }

    @GetMapping("/health")
    public String health() {
        return "LLM service is running!";
    }
}