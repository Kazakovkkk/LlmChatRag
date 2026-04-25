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
    private final GeminiChatService geminiService;
    private final GroqChatService groqService;

    @Value("${llm.provider:ollama}")
    private String provider;

    @Value("${llm.preprocess-provider:ollama}")
    private String preprocessProvider;

    public LlmRouter(OllamaChatService ollamaService,
                     GeminiChatService geminiService,
                     GroqChatService groqService) {
        this.ollamaService = ollamaService;
        this.geminiService = geminiService;
        this.groqService = groqService;
    }

    public Mono<String> chat(String prompt) {
        long start = System.currentTimeMillis();
        log.info("⏱ LLM chat начало | Provider: {}", preprocessProvider);
        return switch (preprocessProvider) {
            case "gemini" -> geminiService.chat(prompt)
                    .doOnSuccess(r -> log.info("⏱ LLM chat: {} мс", System.currentTimeMillis() - start));
            case "groq" -> groqService.chat(prompt)
                    .doOnSuccess(r -> log.info("⏱ LLM chat: {} мс", System.currentTimeMillis() - start));
            default -> ollamaService.chat(prompt)
                    .doOnSuccess(r -> log.info("⏱ LLM chat: {} мс", System.currentTimeMillis() - start));
        };
    }

    public Flux<String> chatStream(String prompt, List<MessageDto> history) {
        long start = System.currentTimeMillis();
        log.info("⏱ LLM stream начало | Provider: {}", provider);
        return switch (provider) {
            case "gemini" -> geminiService.chatStream(prompt, history)
                    .doOnComplete(() -> log.info("⏱ LLM stream: {} мс", System.currentTimeMillis() - start));
            case "groq" -> groqService.chatStream(prompt, history)
                    .doOnComplete(() -> log.info("⏱ LLM stream: {} мс", System.currentTimeMillis() - start));
            default -> ollamaService.chatStream(prompt, history)
                    .doOnComplete(() -> log.info("⏱ LLM stream: {} мс", System.currentTimeMillis() - start));
        };
    }
}