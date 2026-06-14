package llmservice.service;

import llmservice.dto.MessageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class LlmRouter {

    private final OllamaChatService ollamaService;
    private final GroqChatService groqService;
    private final LmStudioChatService lmStudioService;
    private final GigaChatService gigachatService;
    private final StubChatService stubService; // НОВОЕ

    @Value("${llm.provider:ollama}")
    private String provider;

    @Value("${llm.preprocess-provider:ollama-}")
    private String preprocessProvider;

    public LlmRouter(OllamaChatService ollamaService,
                     GroqChatService groqService,
                     LmStudioChatService lmStudioService,
                     GigaChatService gigachatService,
                     StubChatService stubService) { // НОВОЕ
        this.ollamaService = ollamaService;
        this.groqService = groqService;
        this.lmStudioService = lmStudioService;
        this.gigachatService = gigachatService;
        this.stubService = stubService;
    }

    public Mono<String> chat(String prompt) {
        long start = System.currentTimeMillis();
        return switch (preprocessProvider) {
            case "groq" -> groqService.chat(prompt).doOnSuccess(r -> log.info("⏱ LLM chat: {} мс", System.currentTimeMillis() - start));
            case "lmstudio" -> lmStudioService.chat(prompt).doOnSuccess(r -> log.info("⏱ LLM chat: {} мс", System.currentTimeMillis() - start));
            case "gigachat" -> gigachatService.chat(prompt).doOnSuccess(r -> log.info("⏱ LLM chat: {} мс", System.currentTimeMillis() - start));
            case "stub" -> stubService.chat(prompt).doOnSuccess(r -> log.info("⏱ LLM chat (STUB): {} мс", System.currentTimeMillis() - start)); // НОВОЕ
            default -> ollamaService.chat(prompt).doOnSuccess(r -> log.info("⏱ LLM chat: {} мс", System.currentTimeMillis() - start));
        };
    }

    public Flux<String> chatStream(String prompt) {
        long start = System.currentTimeMillis();
        return switch (provider) {
            case "groq" -> groqService.chatStream(prompt).doOnComplete(() -> log.info("⏱ LLM stream: {} мс", System.currentTimeMillis() - start));
            case "lmstudio" -> lmStudioService.chatStream(prompt).doOnComplete(() -> log.info("⏱ LLM stream: {} мс", System.currentTimeMillis() - start));
            case "gigachat" -> gigachatService.chatStream(prompt).doOnComplete(() -> log.info("⏱ LLM stream: {} мс", System.currentTimeMillis() - start));
            case "stub" -> stubService.chatStream(prompt).doOnComplete(() -> log.info("⏱ LLM stream (STUB): {} мс", System.currentTimeMillis() - start)); // НОВОЕ
            default -> ollamaService.chatStream(prompt).doOnComplete(() -> log.info("⏱ LLM stream: {} мс", System.currentTimeMillis() - start));
        };
    }
}