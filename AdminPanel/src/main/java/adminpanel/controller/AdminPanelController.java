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
import adminpanel.dto.GuestManagementDto;
import adminpanel.dto.StaffManagementDto;
import adminpanel.dto.MenuItemManagementDto;
import adminpanel.dto.TicketManagementDto;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AdminPanelController {

    private final AdminManagementService managementService;


    @GetMapping("/dashboard")
    public String dashboardPage(HttpSession session) {
        if (session.getAttribute("hotelKey") == null) {
            return "redirect:/login";
        }
        return "/admin/admin";
    }



    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, String>> uploadPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("mode") String mode,
            HttpSession session) {

        String hotelKey = (String) session.getAttribute("hotelKey");
        if (hotelKey == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
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

    @GetMapping("/chats/history/single")
    @ResponseBody
    public ResponseEntity<List<adminpanel.model.Message>> getSingleChatHistory(
            @RequestParam("hotelKey") String hotelKey,
            @RequestParam("chatId") String chatId) {

        List<adminpanel.model.Message> messages = managementService.getSingleChatMessages(hotelKey, chatId);
        return ResponseEntity.ok(messages);
    }

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
    @GetMapping("/management/guests")
    @ResponseBody
    public ResponseEntity<List<GuestManagementDto>> getGuests(HttpSession session) {
        String hotelKey = (String) session.getAttribute("hotelKey");
        if (hotelKey == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<GuestManagementDto> guests = managementService.getHotelGuests(hotelKey);
        return ResponseEntity.ok(guests);
    }

    @PutMapping("/management/guests")
    @ResponseBody
    public ResponseEntity<Map<String, String>> updateGuest(@RequestBody GuestManagementDto dto, HttpSession session) {
        String hotelKey = (String) session.getAttribute("hotelKey");
        if (hotelKey == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        boolean success = managementService.saveHotelGuest(hotelKey, dto);
        if (success) {
            return ResponseEntity.ok(Map.of("status", "success", "message", "Данные гостя успешно изменены"));
        }
        return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", "Сбой gRPC при обновлении гостя"));
    }

    @GetMapping("/management/staff")
    @ResponseBody
    public ResponseEntity<List<StaffManagementDto>> getStaff(HttpSession session) {
        String hotelKey = (String) session.getAttribute("hotelKey");
        if (hotelKey == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<StaffManagementDto> staff = managementService.getHotelStaffList(hotelKey);
        return ResponseEntity.ok(staff);
    }

    @PutMapping("/management/staff")
    @ResponseBody
    public ResponseEntity<Map<String, String>> updateStaff(@RequestBody StaffManagementDto dto, HttpSession session) {
        String hotelKey = (String) session.getAttribute("hotelKey");
        if (hotelKey == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        boolean success = managementService.saveHotelStaff(hotelKey, dto);
        if (success) {
            return ResponseEntity.ok(Map.of("status", "success", "message", "Статус сотрудника успешно изменен"));
        }
        return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", "Сбой gRPC при обновлении персонала"));
    }

    @GetMapping("/management/menu")
    @ResponseBody
    public ResponseEntity<List<MenuItemManagementDto>> getMenu(HttpSession session) {
        String hotelKey = (String) session.getAttribute("hotelKey");
        if (hotelKey == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<MenuItemManagementDto> menu = managementService.getHotelMenu(hotelKey);
        return ResponseEntity.ok(menu);
    }

    @PutMapping("/management/menu/stock/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> updateStock(
            @PathVariable("id") String itemId,
            @RequestBody Map<String, Integer> payload,
            HttpSession session) {

        String hotelKey = (String) session.getAttribute("hotelKey");
        if (hotelKey == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Integer newStock = payload.get("stockQuantity");
        if (newStock == null || newStock < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Некорректное количество товара"));
        }

        boolean success = managementService.updateFoodStock(hotelKey, itemId, newStock);
        if (success) {
            return ResponseEntity.ok(Map.of("status", "success", "message", "Складские остатки обновлены"));
        }
        return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", "Сбой gRPC при обновлении склада"));
    }


    @GetMapping("/management/tickets")
    @ResponseBody
    public ResponseEntity<List<TicketManagementDto>> getTickets(HttpSession session) {
        String hotelKey = (String) session.getAttribute("hotelKey");
        if (hotelKey == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<TicketManagementDto> tickets = managementService.getHotelActionTickets(hotelKey);
        return ResponseEntity.ok(tickets);
    }

    @PutMapping("/management/tickets/{id}/status")
    @ResponseBody
    public ResponseEntity<Map<String, String>> updateTicketStatus(
            @PathVariable("id") String ticketId,
            @RequestBody Map<String, String> payload,
            HttpSession session) {

        if (session.getAttribute("hotelKey") == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String newStatus = payload.get("status");
        if (newStatus == null || newStatus.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Статус не может быть пустым"));
        }

        boolean success = managementService.changeTicketStatus(ticketId, newStatus);
        if (success) {
            return ResponseEntity.ok(Map.of("status", "success", "message", "Статус заявки успешно изменен"));
        }
        return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", "Сбой gRPC при изменении статуса заявки"));
    }
    @DeleteMapping("/management/staff/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteStaff(@PathVariable("id") String staffId, HttpSession session) {
        if (session.getAttribute("hotelKey") == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        boolean success = managementService.deleteHotelStaff(staffId);
        return success ? ResponseEntity.ok().build() : ResponseEntity.internalServerError().build();
    }

    @PostMapping("/management/menu")
    @ResponseBody
    public ResponseEntity<Void> createMenu(@RequestBody MenuItemManagementDto dto, HttpSession session) {
        String hotelKey = (String) session.getAttribute("hotelKey");
        if (hotelKey == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        boolean success = managementService.addHotelMenu(hotelKey, dto);
        return success ? ResponseEntity.ok().build() : ResponseEntity.internalServerError().build();
    }

    @DeleteMapping("/management/menu/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteMenu(@PathVariable("id") String itemId, HttpSession session) {
        if (session.getAttribute("hotelKey") == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        boolean success = managementService.deleteHotelMenu(itemId);
        return success ? ResponseEntity.ok().build() : ResponseEntity.internalServerError().build();
    }

    @PostMapping("/management/tickets")
    @ResponseBody
    public ResponseEntity<Void> createTicket(@RequestBody TicketManagementDto dto, HttpSession session) {
        String hotelKey = (String) session.getAttribute("hotelKey");
        if (hotelKey == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        boolean success = managementService.addHotelTicket(hotelKey, dto);
        return success ? ResponseEntity.ok().build() : ResponseEntity.internalServerError().build();
    }

    @DeleteMapping("/management/tickets/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteTicket(@PathVariable("id") String ticketId, HttpSession session) {
        if (session.getAttribute("hotelKey") == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        boolean success = managementService.deleteHotelTicket(ticketId);
        return success ? ResponseEntity.ok().build() : ResponseEntity.internalServerError().build();
    }
}