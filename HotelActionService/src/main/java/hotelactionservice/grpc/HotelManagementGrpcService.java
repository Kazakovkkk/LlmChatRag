package hotelactionservice.grpc;

import com.example.grpc.management.HotelManagementServiceGrpc;
import hotelactionservice.entity.FoodMenu;
import hotelactionservice.entity.Guest;
import hotelactionservice.entity.HotelStaff;
import hotelactionservice.entity.ActionTicket;
import hotelactionservice.repository.FoodMenuRepository;
import hotelactionservice.repository.GuestRepository;
import hotelactionservice.repository.HotelStaffRepository;
import hotelactionservice.repository.ActionTicketRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class HotelManagementGrpcService extends com.example.grpc.management.HotelManagementServiceGrpc.HotelManagementServiceImplBase {

    private final GuestRepository guestRepository;
    private final HotelStaffRepository hotelStaffRepository;
    private final FoodMenuRepository foodMenuRepository;
    private final ActionTicketRepository actionTicketRepository;

    // ===================================================================
    // 1. ВКЛАДКА: ГОСТИ
    // ===================================================================
    @Override
    @Transactional(readOnly = true)
    public void getGuests(com.example.grpc.management.GetGuestsRequest request, StreamObserver<com.example.grpc.management.GetGuestsResponse> responseObserver) {
        log.info("📥 gRPC Admin | Запрос списка гостей для отеля: {}", request.getHotelKey());
        List<Guest> guests = guestRepository.findAllByHotelKey(request.getHotelKey());
        com.example.grpc.management.GetGuestsResponse.Builder rb = com.example.grpc.management.GetGuestsResponse.newBuilder();

        guests.forEach(g -> rb.addGuests(com.example.grpc.management.GuestManagementProto.newBuilder()
                .setId(g.getId().toString())
                .setChatId(g.getChatId())
                .setFirstName(g.getFirstName() != null ? g.getFirstName() : "")
                .setLastName(g.getLastName() != null ? g.getLastName() : "")
                .setRoomNumber(g.getRoomNumber() != null ? g.getRoomNumber() : "")
                .setPassportDataEncrypted(g.getPassportDataEncrypted() != null ? g.getPassportDataEncrypted() : "")
                .setStatus(g.getStatus())
                .build()));
        responseObserver.onNext(rb.build());
        responseObserver.onCompleted();
    }

    @Override
    @Transactional
    public void updateGuest(com.example.grpc.management.UpdateGuestRequest request, StreamObserver<com.example.grpc.management.ManagementActionResponse> responseObserver) {
        try {
            com.example.grpc.management.GuestManagementProto p = request.getGuest();
            Guest guest = guestRepository.findById(UUID.fromString(p.getId())).orElse(new Guest());

            if (guest.getId() == null) guest.setId(UUID.fromString(p.getId()));
            guest.setHotelKey(request.getHotelKey());
            guest.setChatId(p.getChatId());
            guest.setFirstName(p.getFirstName());
            guest.setLastName(p.getLastName());
            guest.setRoomNumber(p.getRoomNumber());
            guest.setPassportDataEncrypted(p.getPassportDataEncrypted());
            guest.setStatus(p.getStatus());

            guestRepository.save(guest);
            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder().setSuccess(true).setMessage("Данные гостя сохранены").build());
        } catch (Exception e) {
            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder().setSuccess(false).setMessage(e.getMessage()).build());
        }
        responseObserver.onCompleted();
    }

    // ===================================================================
    // 2. ВКЛАДКА: ПЕРСОНАЛ
    // ===================================================================
    @Override
    @Transactional(readOnly = true)
    public void getStaff(com.example.grpc.management.GetStaffRequest request, StreamObserver<com.example.grpc.management.GetStaffResponse> responseObserver) {
        log.info("📥 gRPC Admin | Запрос списка персонала для отеля: {}", request.getHotelKey());
        List<HotelStaff> staffList = hotelStaffRepository.findAllByHotelKey(request.getHotelKey());
        com.example.grpc.management.GetStaffResponse.Builder rb = com.example.grpc.management.GetStaffResponse.newBuilder();

        staffList.forEach(s -> rb.addStaff(com.example.grpc.management.StaffManagementProto.newBuilder()
                .setId(s.getId().toString())
                .setName(s.getName())
                .setRole(s.getRole())
                .setStatus(s.getStatus())
                .build()));
        responseObserver.onNext(rb.build());
        responseObserver.onCompleted();
    }

    @Override
    @Transactional
    public void updateStaff(com.example.grpc.management.UpdateStaffRequest request, StreamObserver<com.example.grpc.management.ManagementActionResponse> responseObserver) {
        try {
            com.example.grpc.management.StaffManagementProto p = request.getStaff();
            HotelStaff staff = hotelStaffRepository.findById(UUID.fromString(p.getId())).orElse(new HotelStaff());

            if (staff.getId() == null) staff.setId(UUID.fromString(p.getId()));
            staff.setHotelKey(request.getHotelKey());
            staff.setName(p.getName());
            staff.setRole(p.getRole());
            staff.setStatus(p.getStatus());

            hotelStaffRepository.save(staff);
            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder().setSuccess(true).setMessage("Данные сотрудника обновлены").build());
        } catch (Exception e) {
            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder().setSuccess(false).setMessage(e.getMessage()).build());
        }
        responseObserver.onCompleted();
    }

    // ===================================================================
    // 3. ВКЛАДКА: МЕНЮ (Защита цен)
    // ===================================================================
    @Override
    @Transactional(readOnly = true)
    public void getMenu(com.example.grpc.management.GetMenuRequest request, StreamObserver<com.example.grpc.management.GetMenuResponse> responseObserver) {
        log.info("📥 gRPC Admin | Запрос ресторанного меню для отеля: {}", request.getHotelKey());
        List<FoodMenu> items = foodMenuRepository.findAllByHotelKey(request.getHotelKey());
        com.example.grpc.management.GetMenuResponse.Builder rb = com.example.grpc.management.GetMenuResponse.newBuilder();

        items.forEach(m -> rb.addMenuItems(com.example.grpc.management.MenuItemManagementProto.newBuilder()
                .setId(m.getId().toString())
                .setName(m.getName())
                .setPrice(m.getPrice().doubleValue())
                .setStockQuantity(m.getStockQuantity())
                .build()));
        responseObserver.onNext(rb.build());
        responseObserver.onCompleted();
    }

    @Override
    @Transactional
    public void updateMenuStock(com.example.grpc.management.UpdateMenuStockRequest request, StreamObserver<com.example.grpc.management.ManagementActionResponse> responseObserver) {
        log.info("📥 gRPC Admin | Коррекция склада. ID: {}, Остаток: {} шт.", request.getItemId(), request.getStockQuantity());
        try {
            FoodMenu item = foodMenuRepository.findById(UUID.fromString(request.getItemId()))
                    .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));

            // Защита цены: меняем только остаток на складе
            item.setStockQuantity(request.getStockQuantity());
            foodMenuRepository.save(item);

            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder().setSuccess(true).setMessage("Складские остатки обновлены").build());
        } catch (Exception e) {
            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder().setSuccess(false).setMessage(e.getMessage()).build());
        }
        responseObserver.onCompleted();
    }

    // ===================================================================
    // 4. ВКЛАДКА: ЖУРНАЛ ЗАЯВОК ИИ (Бизнес-логика освобождения персонала)
    // ===================================================================
    @Override
    @Transactional(readOnly = true)
    public void getTickets(com.example.grpc.management.GetTicketsRequest request, StreamObserver<com.example.grpc.management.GetTicketsResponse> responseObserver) {
        log.info("📥 gRPC Admin | Запрос лога заявок для отеля: {}", request.getHotelKey());
        List<ActionTicket> tickets = actionTicketRepository.findAllByHotelKeyOrderByCreatedAtDesc(request.getHotelKey());
        com.example.grpc.management.GetTicketsResponse.Builder rb = com.example.grpc.management.GetTicketsResponse.newBuilder();

        tickets.forEach(t -> rb.addTickets(com.example.grpc.management.TicketManagementProto.newBuilder()
                .setId(t.getId().toString())
                .setGuestName(t.getGuest().getFirstName() + " " + t.getGuest().getLastName())
                .setRoomNumber(t.getGuest().getRoomNumber() != null ? t.getGuest().getRoomNumber() : "")
                .setTicketType(t.getTicketType())
                .setStatus(t.getStatus())
                .setAssignedStaffName(t.getAssignedStaff() != null ? t.getAssignedStaff().getName() : "Не назначен")
                .setTotalPrice(t.getTotalPrice() != null ? t.getTotalPrice().doubleValue() : 0.0)
                .setCreatedAt(t.getCreatedAt() != null ? t.getCreatedAt().toString() : "")
                .build()));
        responseObserver.onNext(rb.build());
        responseObserver.onCompleted();
    }

    @Override
    @Transactional
    public void updateTicketStatus(com.example.grpc.management.UpdateTicketStatusRequest request, StreamObserver<com.example.grpc.management.ManagementActionResponse> responseObserver) {
        log.info("📥 gRPC Admin | Изменение статуса тикета {} на {}", request.getTicketId(), request.getNewStatus());
        try {
            ActionTicket ticket = actionTicketRepository.findById(UUID.fromString(request.getTicketId()))
                    .orElseThrow(() -> new IllegalArgumentException("Тикет не найден"));

            String oldStatus = ticket.getStatus();
            String newStatus = request.getNewStatus().toUpperCase();
            ticket.setStatus(newStatus);

            // Автоматическое освобождение сотрудников отеля при завершении/отмене задачи
            if (ticket.getAssignedStaff() != null && ("COMPLETED".equals(newStatus) || "REJECTED".equals(newStatus))) {
                if ("IN_PROGRESS".equals(oldStatus)) {
                    ticket.getAssignedStaff().setStatus("FREE");
                    log.info("💼 Автоматическое освобождение сотрудника: {}", ticket.getAssignedStaff().getName());
                }
            }

            actionTicketRepository.save(ticket);
            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder().setSuccess(true).setMessage("Статус заявки обновлен").build());
        } catch (Exception e) {
            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder().setSuccess(false).setMessage(e.getMessage()).build());
        }
        responseObserver.onCompleted();
    }
}