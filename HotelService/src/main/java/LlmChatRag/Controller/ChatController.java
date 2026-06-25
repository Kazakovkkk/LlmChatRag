package LlmChatRag.Controller;

import LlmChatRag.dto.ChatRequest;
import LlmChatRag.service.RagChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
//http://localhost:8080/?hotel=cosmos
//jmeter -n -t Thread_Group_RAG.jmx -l results_50.jtl -e -o report_50
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final RagChatService ragChatService;

    @PostMapping
    public String chat(@RequestBody ChatRequest request) {
        return ragChatService.chat(request.getMessage(), request.getHotelKey(),
                request.getHistory() != null ? request.getHistory() : List.of() );
    }
    @PostMapping(value = "/test_fine")
    public String chattest(@RequestBody ChatRequest request) {
        return ragChatService.chat_test(request.getMessage(), request.getHotelKey(),
                request.getHistory() != null ? request.getHistory() : List.of() );
    }


    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        return ragChatService.chatStream(
                request.getHotelKey(),
                request.getChatId(),
                request.getMessage(),
                request.getHistory() != null ? request.getHistory() : List.of(),
                request.getTimestamp()
        );
    }

    @GetMapping("/history")
    public List<LlmChatRag.dto.MessageDto> getChatHistory(
            @RequestParam String hotelKey,
            @RequestParam String chatId) {
        //log.info("Запрос на восстановление истории для отеля: {}, сессия: {}", hotelKey, chatId);
        return ragChatService.getChatHistoryFromAdmin(hotelKey, chatId);
    }

    @GetMapping("/health")
    public String health() {
        return "Hotel service is running!";
    }
}