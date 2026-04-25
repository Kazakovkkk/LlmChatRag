// service/LlmStreamRouter.java
package LlmChatRag.service;

import LlmChatRag.dto.AnswerRequest;
import LlmChatRag.grpc.LlmGrpcClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import java.util.Map;

@Slf4j
@Service
public class LlmStreamRouter {

    private final WebClient llmWebClient;
    private final LlmGrpcClient llmGrpcClient;
    private final ObjectMapper objectMapper;

    @Value("${llm.stream.protocol:rest}")
    private String protocol;

    public LlmStreamRouter(
            @Qualifier("llmWebClient") WebClient llmWebClient,
            LlmGrpcClient llmGrpcClient,
            ObjectMapper objectMapper) {
        this.llmWebClient = llmWebClient;
        this.llmGrpcClient = llmGrpcClient;
        this.objectMapper = objectMapper;
    }

    public Flux<String> stream(AnswerRequest request) {
        log.info("⏱ LLM стриминг | Protocol: {}", protocol.toUpperCase());

        return switch (protocol) {
            case "grpc" -> streamViaGrpc(request);
            default -> streamViaRest(request);
        };
    }

    public String getProtocol() {
        return protocol;
    }

    private Flux<String> streamViaGrpc(AnswerRequest request) {
        log.info("→ gRPC stream | вопрос: '{}'", request.getQuestion());
        long start = System.currentTimeMillis();

        return llmGrpcClient.answerStream(request)
                .map(token -> {
                    try {
                        return objectMapper.writeValueAsString(Map.of("token", token));
                    } catch (Exception e) {
                        return "{\"token\":\"\"}";
                    }
                })
                .doOnComplete(() -> log.info("⏱ gRPC stream завершён | {} мс",
                        System.currentTimeMillis() - start));
    }

    private Flux<String> streamViaRest(AnswerRequest request) {
        log.info("→ REST stream | вопрос: '{}'", request.getQuestion());
        long start = System.currentTimeMillis();

        return llmWebClient.post()
                .uri("/llm/answer/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnComplete(() -> log.info("⏱ REST stream завершён | {} мс",
                        System.currentTimeMillis() - start));
    }
}