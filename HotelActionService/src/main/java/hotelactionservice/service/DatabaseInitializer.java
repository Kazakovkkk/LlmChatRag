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
import java.util.UUID;

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
            Guest guest = new Guest();
            guest.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            guest.setHotelKey(hotelKey);
            guest.setChatId(testChatId);
            guest.setFirstName("Иван");
            guest.setLastName("Иванов");
            guest.setRoomNumber("505");
            guest.setPassportDataEncrypted("PASSPORT_COSMOS_8877");
            guest.setStatus("CHECKED_IN");

            guestRepository.save(guest);
            log.info("✅ Тестовый гость Иван (комната 505) успешно добавлен для отеля '{}'", hotelKey);
        }

        // 2. Инициализация персонала отеля (Услуги горничных и официантов)
        initStaffIfNotExist(hotelKey, "22222222-2222-2222-2222-222222222222", "Наталья (свободна)", "MAID", "FREE");
        initStaffIfNotExist(hotelKey, "33333333-3333-3333-3333-333333333333", "Ольга (занята)", "MAID", "BUSY");
        initStaffIfNotExist(hotelKey, "44444444-4444-4444-4444-444444444444", "Дмитрий (официант)", "WAITER", "FREE");

        // 3. Инициализация ресторанного меню
        initFoodItemIfNotExist(hotelKey, "55555555-5555-5555-5555-555555555555", "пицца", new BigDecimal("790.00"), 10);
        initFoodItemIfNotExist(hotelKey, "66666666-6666-6666-6666-666666666666", "суп", new BigDecimal("380.00"), 0); // Закончился
        initFoodItemIfNotExist(hotelKey, "77777777-7777-7777-7777-777777777777", "кола", new BigDecimal("150.00"), 25);

        log.info("🎯 [DatabaseInitializer] Проверка и наполнение тестовой БД завершены.");
    }

    private void initStaffIfNotExist(String hotelKey, String uuidStr, String name, String role, String status) {
        UUID id = UUID.fromString(uuidStr);
        if (!hotelStaffRepository.existsById(id)) {
            HotelStaff staff = new HotelStaff(id, hotelKey, name, role, status);
            hotelStaffRepository.save(staff);
            log.info("✅ Сотрудник {} ({}) добавлен в штат отеля '{}'", name, role, hotelKey);
        }
    }

    private void initFoodItemIfNotExist(String hotelKey, String uuidStr, String name, BigDecimal price, int stock) {
        if (foodMenuRepository.findByHotelKeyAndNameIgnoreCase(hotelKey, name).isEmpty()) {
            FoodMenu item = new FoodMenu(UUID.fromString(uuidStr), hotelKey, name, price, stock);
            foodMenuRepository.save(item);
            log.info("✅ Блюдо '{}' (Цена: {} руб, Остаток: {} шт) добавлено в меню отеля '{}'", name, price, stock, hotelKey);
        }
    }
}