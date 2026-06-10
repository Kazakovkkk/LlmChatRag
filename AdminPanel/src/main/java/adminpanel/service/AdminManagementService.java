package adminpanel.service;

import adminpanel.dto.*;
import adminpanel.grpc.HotelManagementGrpcClient;
import adminpanel.grpc.RagGrpcClient;
import adminpanel.model.Chat;
import adminpanel.model.Message;
import adminpanel.model.User;
import adminpanel.repository.ChatRepository;
import adminpanel.repository.MessageRepository;
import adminpanel.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminManagementService {

    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final RagGrpcClient ragGrpcClient;
    private final HotelManagementGrpcClient hotelManagementGrpcClient;
    // Автоматическое создание тестового админа при старте приложения
    @PostConstruct
    public void initDefaultAdmin() {
        if (userRepository.findByUsername("admin@cosmos.ru").isEmpty()) {
            User defaultAdmin = User.builder()
                    .username("admin@cosmos.ru")
                    .passwordHash("password123") // В проде здесь должен быть BCrypt хэш
                    .hotelKey("cosmos")
                    .role("ROLE_ADMIN")
                    .build();
            userRepository.save(defaultAdmin);
            log.info("🚀 Создан тестовый администратор отеля Cosmos: admin@cosmos.ru / password123");
        }
    }

    // Метод проверки учетных данных
    public Optional<User> authenticate(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(user -> user.getPasswordHash().equals(password)); // Простая проверка для демонстрации
    }

    // --- Функционал управления базой знаний ---

    public List<DocumentDto> getHotelKnowledgeBase(String hotelKey) {
        return ragGrpcClient.fetchChunks(hotelKey, 100);
    }

    public boolean modifyKnowledgeChunk(String hotelKey, String chunkId, String newText) {
        return ragGrpcClient.updateChunk(hotelKey, chunkId, newText);
    }

    // Модифицируем метод отправки в файле AdminManagementService.java

    public boolean processPdfUpload(String hotelKey, byte[] bytes, String filename, String mode) {
        // Передаем mode в gRPC клиент (в следующем шаге gRPC клиент запишет его в protobuf структуру)
        return ragGrpcClient.uploadDocument(hotelKey, bytes, filename, mode);
    }
    // --- Функционал аудита и коррекции чатов ---

    @Transactional(readOnly = true)
    public List<Chat> getHotelChatHistory(String hotelKey) {
        return chatRepository.findAllByHotelKeyOrderByCreatedAtDesc(hotelKey);
    }

    @Transactional
    public void modifyBotResponse(Long messageId, String correctedText) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Сообщение не найдено"));

        if (!"assistant".equals(message.getRole())) {
            throw new IllegalStateException("Администратор может редактировать только ответы ИИ-ассистента");
        }

        message.setContent(correctedText);
        messageRepository.save(message);
    }

    @Transactional
    public void logIncomingMessage(String hotelKey, String chatId, String role, String content) {
        Chat chat = chatRepository.findById(chatId).orElseGet(() -> {
            Chat newChat = Chat.builder()
                    .id(chatId)
                    .hotelKey(hotelKey)
                    .createdAt(LocalDateTime.now())
                    .build();
            return chatRepository.save(newChat);
        });

        Message msg = Message.builder()
                .chat(chat)
                .role(role)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();

        messageRepository.save(msg);
    }
    // Добавь этот метод в AdminManagementService.java

    @Transactional(readOnly = true)
    public List<Message> getSingleChatMessages(String hotelKey, String chatId) {
        return chatRepository.findById(chatId)
                .filter(chat -> chat.getHotelKey().equalsIgnoreCase(hotelKey.trim()))
                .map(Chat::getMessages) // Вытаскиваем только сообщения этого чанка
                .orElse(List.of());     // Если чат еще не создан, возвращаем пустой список
    }
    public List<GuestManagementDto> getHotelGuests(String hotelKey) {
        return hotelManagementGrpcClient.fetchGuests(hotelKey);
    }

    public boolean saveHotelGuest(String hotelKey, GuestManagementDto dto) {
        return hotelManagementGrpcClient.updateGuest(hotelKey, dto);
    }

    public List<StaffManagementDto> getHotelStaffList(String hotelKey) {
        return hotelManagementGrpcClient.fetchStaff(hotelKey);
    }

    public boolean saveHotelStaff(String hotelKey, StaffManagementDto dto) {
        return hotelManagementGrpcClient.updateStaff(hotelKey, dto);
    }

    public List<MenuItemManagementDto> getHotelMenu(String hotelKey) {
        return hotelManagementGrpcClient.fetchMenu(hotelKey);
    }

    public boolean updateFoodStock(String hotelKey, String itemId, int stockQuantity) {
        return hotelManagementGrpcClient.updateMenuStock(hotelKey, itemId, stockQuantity);
    }

    public List<TicketManagementDto> getHotelActionTickets(String hotelKey) {
        return hotelManagementGrpcClient.fetchTickets(hotelKey);
    }

    public boolean changeTicketStatus(String ticketId, String newStatus) {
        return hotelManagementGrpcClient.updateTicketStatus(ticketId, newStatus);
    }
}