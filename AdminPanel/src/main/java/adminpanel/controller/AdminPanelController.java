package adminpanel.controller;

import adminpanel.dto.DocumentDto;
import adminpanel.model.Chat;
import adminpanel.service.AdminManagementService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Controller // Меняем на @Controller, чтобы возвращать и HTML-страницы, и JSON-данные
@RequestMapping("/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AdminPanelController {

    private final AdminManagementService managementService;

    // Страница панели управления (Доступна только авторизованным)
    @GetMapping("/dashboard")
    public String dashboardPage(HttpSession session) {
        if (session.getAttribute("hotelKey") == null) {
            return "redirect:/login";
        }
        return "/admin/admin";
    }

    // --- Защищенные REST Эндпоинты ---

    // Находим и заменяем старый метод обработки в файле AdminPanelController.java

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, String>> uploadPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("mode") String mode, // <-- ИСПРАВЛЕНИЕ: Добавлен приём режима (APPEND/OVERWRITE)
            HttpSession session) {

        String hotelKey = (String) session.getAttribute("hotelKey");
        if (hotelKey == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            // Транслируем параметр mode в управляющий сервис
            boolean success = managementService.processPdfUpload(hotelKey, file.getBytes(), file.getOriginalFilename(), mode);

            if (success) {
                return ResponseEntity.ok(Map.of("status", "success", "message", "Файл успешно обработан в режиме " + mode));
            }
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", "Сбой gRPC шлюза"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/knowledge-base")
    @ResponseBody
    public ResponseEntity<List<DocumentDto>> getKnowledgeBase(HttpSession session) {
        String hotelKey = (String) session.getAttribute("hotelKey");
        if (hotelKey == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        return ResponseEntity.ok(managementService.getHotelKnowledgeBase(hotelKey));
    }

    @PutMapping("/knowledge-base/chunk/{id}")
    @ResponseBody
    public ResponseEntity<Void> updateChunk(
            @PathVariable("id") String chunkId,
            @RequestBody Map<String, String> payload,
            HttpSession session) {

        String hotelKey = (String) session.getAttribute("hotelKey");
        if (hotelKey == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        boolean success = managementService.modifyKnowledgeChunk(hotelKey, chunkId, payload.get("text"));
        return success ? ResponseEntity.ok().build() : ResponseEntity.internalServerError().build();
    }

    @GetMapping("/chats/history")
    @ResponseBody
    public ResponseEntity<List<Chat>> getHistory(HttpSession session) {
        String hotelKey = (String) session.getAttribute("hotelKey");
        if (hotelKey == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        return ResponseEntity.ok(managementService.getHotelChatHistory(hotelKey));
    }

    @PutMapping("/chats/message/{id}")
    @ResponseBody
    public ResponseEntity<Void> correctMessage(@PathVariable("id") Long messageId, @RequestBody Map<String, String> payload, HttpSession session) {
        if (session.getAttribute("hotelKey") == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        managementService.modifyBotResponse(messageId, payload.get("text"));
        return ResponseEntity.ok().build();
    }
    // Добавь этот метод в AdminPanelController.java

    // Внутренний эндпоинт для восстановления истории ОДНОЙ конкретной сессии гостя
    @GetMapping("/chats/history/single")
    @ResponseBody
    public ResponseEntity<List<adminpanel.model.Message>> getSingleChatHistory(
            @RequestParam("hotelKey") String hotelKey,
            @RequestParam("chatId") String chatId) {

        // Этот метод вызывается без HttpSession, данные берутся строго из параметров запроса
        List<adminpanel.model.Message> messages = managementService.getSingleChatMessages(hotelKey, chatId);
        return ResponseEntity.ok(messages);
    }

    // Внешняя синхронизация сообщений (Без сессии, вызывается по межсервисному REST)
    @PostMapping("/chats/sync")
    @ResponseBody
    public ResponseEntity<Void> syncMessage(@RequestBody Map<String, String> payload) {
        managementService.logIncomingMessage(
                payload.get("hotelKey"),
                payload.get("chatId"),
                payload.get("role"),
                payload.get("content")
        );
        return ResponseEntity.ok().build();
    }
}