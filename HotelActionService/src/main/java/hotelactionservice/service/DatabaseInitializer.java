package hotelactionservice.service;

import hotelactionservice.entity.FoodMenu;
import hotelactionservice.entity.Guest;
import hotelactionservice.entity.HotelStaff;
import hotelactionservice.repository.FoodMenuRepository;
import hotelactionservice.repository.GuestRepository;
import hotelactionservice.repository.HotelStaffRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseInitializer {

    private final GuestRepository guestRepository;
    private final HotelStaffRepository hotelStaffRepository;
    private final FoodMenuRepository foodMenuRepository;

    @PostConstruct
    @Transactional
    public void initTestData() {
        log.info("🚀 [DatabaseInitializer] Запуск автоматического наполнения БД тестовыми данными...");

        String hotelKey = "cosmos";

        // 1. Инициализация тестового гостя
        String testChatId = "guest_opuabappze";
        if (guestRepository.findByChatIdAndHotelKey(testChatId, hotelKey).isEmpty()) {
            Guest guest = Guest.builder()
                    .hotelKey(hotelKey)
                    .chatId(testChatId)
                    .firstName("Иван")
                    .lastName("Иванов")
                    .roomNumber("505")
                    .passportDataEncrypted("PASSPORT_COSMOS_8877")
                    .status("CHECKED_IN")
                    .build(); // 🌟 ID НЕ УКАЗЫВАЕМ! Он будет null, и Hibernate сделает чистый INSERT

            guestRepository.save(guest);
            log.info("Тестовый гость Иван (комната 505) успешно добавлен для отеля '{}'", hotelKey);
        }

        // 2. Инициализация персонала отеля (Проверяем по имени, так как ID теперь генерирует БД)
        initStaffIfNotExist(hotelKey, "Наталья (свободна)", "MAID", "FREE");
        initStaffIfNotExist(hotelKey, "Ольга (занята)", "MAID", "BUSY");
        initStaffIfNotExist(hotelKey, "Дмитрий (официант)", "WAITER", "FREE");

        // 3. Инициализация ресторанного меню (Проверяем по названию блюда)
        initFoodItemIfNotExist(hotelKey, "пицца", new BigDecimal("790.00"), 10);
        initFoodItemIfNotExist(hotelKey, "суп", new BigDecimal("380.00"), 0);
        initFoodItemIfNotExist(hotelKey, "кола", new BigDecimal("150.00"), 25);

        log.info("[DatabaseInitializer] Проверка и наполнение тестовой БД завершены.");
    }

    private void initStaffIfNotExist(String hotelKey, String name, String role, String status) {
        // Проверяем наличие по имени в рамках конкретного отеля
        boolean exists = hotelStaffRepository.findAllByHotelKey(hotelKey).stream()
                .anyMatch(staff -> staff.getName().equalsIgnoreCase(name));

        if (!exists) {
            HotelStaff staff = HotelStaff.builder()
                    .hotelKey(hotelKey)
                    .name(name)
                    .role(role)
                    .status(status)
                    .build(); // 🌟 ID генерируется автоматически через IDENTITY
            hotelStaffRepository.save(staff);
            log.info("Сотрудник {} ({}) добавлен в штат отеля '{}'", name, role, hotelKey);
        }
    }

    private void initFoodItemIfNotExist(String hotelKey, String name, BigDecimal price, int stock) {
        if (foodMenuRepository.findByHotelKeyAndNameIgnoreCase(hotelKey, name).isEmpty()) {
            FoodMenu item = FoodMenu.builder()
                    .hotelKey(hotelKey)
                    .name(name)
                    .price(price)
                    .stockQuantity(stock)
                    .build(); // 🌟 ID генерируется автоматически через IDENTITY
            foodMenuRepository.save(item);
            log.info("Блюдо '{}' (Цена: {} руб, Остаток: {} шт) добавлено в меню отеля '{}'", name, price, stock, hotelKey);
        }
    }
}