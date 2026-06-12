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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActionExecutorService {

    private final GuestRepository guestRepository;
    private final HotelStaffRepository hotelStaffRepository;
    private final FoodMenuRepository foodMenuRepository;
    private final ActionTicketRepository actionTicketRepository;

    @Transactional
    public ActionResponse processAction(String hotelKey, String chatId, String actionName, Map<String, String> parameters) {
        log.info("🎯 [ActionExecutorEngine] Запуск транзакции '{}' для отеля '{}', сессия: {}", actionName, hotelKey, chatId);

        Guest guest = guestRepository.findByChatIdAndHotelKey(chatId, hotelKey).orElse(null);

        if (guest == null) {
            log.warn("Верификация провалена: гость с chatId {} не найден в отеле {}", chatId, hotelKey);
            return new ActionResponse(
                    false,
                    "UNAUTHORIZED",
                    "⚠ Действие отклонено. Система не смогла верифицировать ваш профиль проживающего. Пожалуйста, обратитесь на стойку регистрации (Reception).",
                    Map.of()
            );
        }

        if (!"CHECKED_IN".equalsIgnoreCase(guest.getStatus())) {
            return new ActionResponse(
                    false,
                    "REJECTED",
                    String.format("Уважаемый %s, ваш статус в системе — выписан. Заказ услуг невозможен.", guest.getFirstName()),
                    Map.of()
            );
        }

        switch (actionName.toUpperCase()) {
            case "ROOM_CLEANING":
                return handleRoomCleaning(hotelKey, guest);
            case "ORDER_FOOD":
                return handleOrderFood(hotelKey, guest, parameters);
            default:
                log.warn("⚠ Неизвестное транзакционное намерение: {}", actionName);
                return new ActionResponse(
                        false,
                        "NOT_SUPPORTED",
                        "Данный тип интеграционного запроса временно не поддерживается СУБД отеля.",
                        Map.of()
                );
        }
    }

    private ActionResponse handleRoomCleaning(String hotelKey, Guest guest) {
        List<HotelStaff> freeMaids = hotelStaffRepository.findByHotelKeyAndRoleAndStatus(hotelKey, "MAID", "FREE");

        ActionTicket ticket = new ActionTicket();
        ticket.setHotelKey(hotelKey);
        ticket.setGuest(guest);
        ticket.setTicketType("ROOM_CLEANING");

        Map<String, String> details = new HashMap<>();
        String message;

        if (!freeMaids.isEmpty()) {
            HotelStaff assignedMaid = freeMaids.get(0);
            assignedMaid.setStatus("BUSY");
            hotelStaffRepository.save(assignedMaid);

            ticket.setStatus("IN_PROGRESS");
            ticket.setAssignedStaff(assignedMaid);

            message = String.format("Отличная новость, %s! Ваша заявка на уборку комнаты %s принята. К вам направлена горничная %s. Она будет у вас в течение 15 минут.",
                    guest.getFirstName(), guest.getRoomNumber(), assignedMaid.getName());

            details.put("assigned_staff_name", assignedMaid.getName());
            details.put("ticket_status", "IN_PROGRESS");
        } else {
            ticket.setStatus("QUEUED");

            message = String.format("%s, сейчас все наши горничные заняты на вызовах. Мы внесли вашу заявку на уборку номера %s в электронную очередь. Она будет выполнена автоматически, как только освободится первый сотрудник.",
                    guest.getFirstName(), guest.getRoomNumber());

            details.put("ticket_status", "QUEUED");
        }

        // Сохраняем, ID сгенерируется автоматически базой данных (IDENTITY)
        ActionTicket savedTicket = actionTicketRepository.save(ticket);
        details.put("ticket_id", String.valueOf(savedTicket.getId()));

        return new ActionResponse(true, savedTicket.getStatus(), message, details);
    }

    private ActionResponse handleOrderFood(String hotelKey, Guest guest, Map<String, String> parameters) {
        String dishName = parameters.getOrDefault("dish", "Пицца");
        FoodMenu foodItem = foodMenuRepository.findByHotelKeyAndNameIgnoreCase(hotelKey, dishName).orElse(null);

        if (foodItem == null) {
            return new ActionResponse(
                    false,
                    "NOT_FOUND",
                    String.format("Извините, блюдо '%s' отсутствует в меню ресторана нашего отеля.", dishName),
                    Map.of()
            );
        }

        if (foodItem.getStockQuantity() <= 0) {
            return new ActionResponse(
                    false,
                    "OUT_OF_STOCK",
                    String.format("К сожалению, '%s' закончился на кухне ресторана. Пожалуйста, выберите другое блюдо.", foodItem.getName()),
                    Map.of()
            );
        }

        foodItem.setStockQuantity(foodItem.getStockQuantity() - 1);
        foodMenuRepository.save(foodItem);

        ActionTicket ticket = new ActionTicket();
        ticket.setHotelKey(hotelKey);
        ticket.setGuest(guest);
        ticket.setTicketType("ORDER_FOOD");
        ticket.setStatus("COMPLETED");
        ticket.setTotalPrice(foodItem.getPrice());

        List<HotelStaff> freeWaiters = hotelStaffRepository.findByHotelKeyAndRoleAndStatus(hotelKey, "WAITER", "FREE");
        if (!freeWaiters.isEmpty()) {
            ticket.setAssignedStaff(freeWaiters.get(0));
        }

        ActionTicket savedTicket = actionTicketRepository.save(ticket);

        String message = String.format("🎉 Заказ оформлен! %s, мы списали со склада 1 шт. '%s'. Сумма заказа: %s руб. записана на ваш номер %s. Доставка в номер займет 30 минут.",
                guest.getFirstName(), foodItem.getName(), foodItem.getPrice(), guest.getRoomNumber());

        Map<String, String> details = Map.of(
                "ticket_id", String.valueOf(savedTicket.getId()),
                "item_ordered", foodItem.getName(),
                "price_charged", foodItem.getPrice().toString(),
                "remaining_stock", foodItem.getStockQuantity().toString()
        );

        return new ActionResponse(true, "CONFIRMED", message, details);
    }
}