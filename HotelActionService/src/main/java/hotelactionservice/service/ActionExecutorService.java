package hotelactionservice.service;

import hotelactionservice.dto.ActionResponse;
import hotelactionservice.entity.ActionTicket;
import hotelactionservice.entity.FoodMenu;
import hotelactionservice.entity.Guest;
import hotelactionservice.entity.HotelStaff;
import hotelactionservice.repository.ActionTicketRepository;
import hotelactionservice.repository.FoodMenuRepository;
import hotelactionservice.repository.GuestRepository;
import hotelactionservice.repository.HotelStaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActionExecutorService {

    private final GuestRepository guestRepository;
    private final HotelStaffRepository hotelStaffRepository;
    private final FoodMenuRepository foodMenuRepository;
    private final ActionTicketRepository actionTicketRepository;

    /**
     * Центральный метод обработки транзакций, запускаемый ИИ-препроцессором
     */
    @Transactional
    public ActionResponse processAction(String hotelKey, String chatId, String actionName, Map<String, String> parameters) {
        log.info("🎯 [ActionExecutorEngine] Запуск транзакции '{}' для отеля '{}', сессия: {}",
                actionName, hotelKey, chatId);

        // 1. Верификация гостя: проверяем, зарегистрирован ли этот chatId в PMS отеля
        Guest guest = guestRepository.findByChatIdAndHotelKey(chatId, hotelKey)
                .orElse(null);

        if (guest == null) {
            log.warn("❌ Верификация провалена: гость с chatId {} не найден в отеле {}", chatId, hotelKey);
            return new ActionResponse(
                    false,
                    "UNAUTHORIZED",
                    "⚠️ Действие отклонено. Система не смогла верифицировать ваш профиль проживающего. Пожалуйста, обратитесь на стойку регистрации (Reception).",
                    Map.of()
            );
        }

        // Проверяем, что гость действительно проживает в данный момент
        if (!"CHECKED_IN".equalsIgnoreCase(guest.getStatus())) {
            return new ActionResponse(
                    false,
                    "REJECTED",
                    String.format("Уважаемый %s, ваш статус в системе — выписан. Заказ услуг невозможен.", guest.getFirstName()),
                    Map.of()
            );
        }

        // 2. Ветвление бизнес-логики на основе распознанного экшена
        switch (actionName.toUpperCase()) {
            case "ROOM_CLEANING":
                return handleRoomCleaning(hotelKey, guest);

            case "ORDER_FOOD":
                return handleOrderFood(hotelKey, guest, parameters);

            default:
                log.warn("⚠️ Неизвестное транзакционное намерение: {}", actionName);
                return new ActionResponse(
                        false,
                        "NOT_SUPPORTED",
                        "Данный тип интеграционного запроса временно не поддерживается СУБД отеля.",
                        Map.of()
                );
        }
    }

    /**
     * Сценарий А: Вызов горничной и смена её статуса на 'BUSY'
     */
    private ActionResponse handleRoomCleaning(String hotelKey, Guest guest) {
        // Ищем первую попавшуюся свободную горничную (MAID) в этом отеле
        List<HotelStaff> freeMaids = hotelStaffRepository.findByHotelKeyAndRoleAndStatus(hotelKey, "MAID", "FREE");

        ActionTicket ticket = new ActionTicket();
        ticket.setId(UUID.randomUUID());
        ticket.setHotelKey(hotelKey);
        ticket.setGuest(guest);
        ticket.setTicketType("ROOM_CLEANING");

        Map<String, String> details = new HashMap<>();
        String message;

        if (!freeMaids.isEmpty()) {
            // Нашли свободного сотрудника!
            HotelStaff assignedMaid = freeMaids.get(0);

            // Меняем статус горничной в базе данных на 'BUSY' (Занята)
            assignedMaid.setStatus("BUSY");
            hotelStaffRepository.save(assignedMaid);

            // Оформляем тикет со статусом IN_PROGRESS
            ticket.setStatus("IN_PROGRESS");
            ticket.setAssignedStaff(assignedMaid);

            message = String.format("✨ Отличная новость, %s! Ваша заявка на уборку комнаты %s принята. К вам направлена горничная %s. Она будет у вас в течение 15 минут.",
                    guest.getFirstName(), guest.getRoomNumber(), assignedMaid.getName());

            details.put("assigned_staff_name", assignedMaid.getName());
            details.put("ticket_status", "IN_PROGRESS");
        } else {
            // Если все горничные заняты, не отклоняем запрос, а ставим его в очередь СУБД (QUEUED)
            ticket.setStatus("QUEUED");

            message = String.format("⏳ %s, сейчас все наши горничные заняты на вызовах. Мы внесли вашу заявку на уборку номера %s в электронную очередь. Она будет выполнена автоматически, как только освободится первый сотрудник.",
                    guest.getFirstName(), guest.getRoomNumber());

            details.put("ticket_status", "QUEUED");
        }

        actionTicketRepository.save(ticket);
        return new ActionResponse(true, ticket.getStatus(), message, details);
    }

    /**
     * Сценарий Б: Заказ еды с автоматической калькуляцией стоимости и списанием остатков
     */
    private ActionResponse handleOrderFood(String hotelKey, Guest guest, Map<String, String> parameters) {
        // Извлекаем переданное ИИ название блюда (по умолчанию ищем "Пицца")
        String dishName = parameters.getOrDefault("dish", "Пицца");

        // Ищем позицию в меню отеля (без учета регистра букв)
        FoodMenu foodItem = foodMenuRepository.findByHotelKeyAndNameIgnoreCase(hotelKey, dishName)
                .orElse(null);

        if (foodItem == null) {
            return new ActionResponse(
                    false,
                    "NOT_FOUND",
                    String.format("🍕 Извините, блюдо '%s' отсутствует в меню ресторана нашего отеля.", dishName),
                    Map.of()
            );
        }

        // Проверяем складские остатки (stock_quantity) в PostgreSQL
        if (foodItem.getStockQuantity() <= 0) {
            return new ActionResponse(
                    false,
                    "OUT_OF_STOCK",
                    String.format("😔 К сожалению, '%s' закончился на кухне ресторана. Пожалуйста, выберите другое блюдо.", foodItem.getName()),
                    Map.of()
            );
        }

        // Списываем 1 единицу товара со склада
        foodItem.setStockQuantity(foodItem.getStockQuantity() - 1);
        foodMenuRepository.save(foodItem);

        // Регистрируем финансовый чек-тикет
        ActionTicket ticket = new ActionTicket();
        ticket.setId(UUID.randomUUID());
        ticket.setHotelKey(hotelKey);
        ticket.setGuest(guest);
        ticket.setTicketType("ORDER_FOOD");
        ticket.setStatus("COMPLETED"); // Считаем транзакцию покупки сразу успешно завершенной
        ticket.setTotalPrice(foodItem.getPrice());

        // Автоматически назначаем свободного официанта (WAITER), если он есть
        List<HotelStaff> freeWaiters = hotelStaffRepository.findByHotelKeyAndRoleAndStatus(hotelKey, "WAITER", "FREE");
        if (!freeWaiters.isEmpty()) {
            ticket.setAssignedStaff(freeWaiters.get(0));
        }

        actionTicketRepository.save(ticket);

        String message = String.format("🎉 Заказ оформлен! %s, мы списали со склада 1 шт. '%s'. Сумма заказа: %s руб. записана на ваш номер %s. Доставка в номер займет 30 минут.",
                guest.getFirstName(), foodItem.getName(), foodItem.getPrice(), guest.getRoomNumber());

        Map<String, String> details = Map.of(
                "item_ordered", foodItem.getName(),
                "price_charged", foodItem.getPrice().toString(),
                "remaining_stock", foodItem.getStockQuantity().toString()
        );

        return new ActionResponse(true, "CONFIRMED", message, details);
    }
}