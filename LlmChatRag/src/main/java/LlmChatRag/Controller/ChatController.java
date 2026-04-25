package LlmChatRag.Controller;

import LlmChatRag.dto.ChatRequest;
import LlmChatRag.service.RagChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagChatService ragChatService;

    @PostMapping
    public String chat(@RequestBody ChatRequest request) {
        return ragChatService.chat(request.getMessage(),
                request.getHistory() != null ? request.getHistory() : List.of() );
    }

    @PostMapping(value = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        return ragChatService.chatStream(
                request.getMessage(),
                request.getHistory() != null ? request.getHistory() : List.of(),
                request.getTimestamp()
        );
    }

    @GetMapping("/health")
    public String health() {
        return "Hotel service is running!";
    }
}