package LlmChatRag.service;

import LlmChatRag.dto.MessageDto;
import LlmChatRag.dto.PreprocessedQuestion;
import LlmChatRag.grpc.LlmGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LlmPreprocessorRouter {

    private final RestClient llmRestClient;
    private final LlmGrpcClient llmGrpcClient;

    @Value("${llm.preprocess.protocol:rest}") // ← Свойство переключения протокола в application.properties
    private String protocol;

    public LlmPreprocessorRouter(@Qualifier("llmRestClient") RestClient llmRestClient, LlmGrpcClient llmGrpcClient) {
        this.llmRestClient = llmRestClient;
        this.llmGrpcClient = llmGrpcClient;
    }

    public PreprocessedQuestion preprocess(String question, List<MessageDto> history) {
        log.info("⏱ Маршрутизация препроцессинга [Protocol: {}] | Вопрос: '{}'", protocol.toUpperCase(), question);

        if ("grpc".equalsIgnoreCase(protocol)) {
            return llmGrpcClient.preprocessQuestion(question, history);
        } else {
            Map<String, Object> body = new HashMap<>();
            body.put("question", question);
            body.put("history", history);

            return llmRestClient.post()
                    .uri("/llm/preprocess")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(PreprocessedQuestion.class);
        }
    }
}