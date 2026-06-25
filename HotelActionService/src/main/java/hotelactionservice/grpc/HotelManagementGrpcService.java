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
import java.util.List;
import java.math.BigDecimal;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class HotelManagementGrpcService extends HotelManagementServiceGrpc.HotelManagementServiceImplBase {

    private final GuestRepository guestRepository;
    private final HotelStaffRepository hotelStaffRepository;
    private final FoodMenuRepository foodMenuRepository;
    private final ActionTicketRepository actionTicketRepository;

    @Override
    @Transactional(readOnly = true)
    public void getGuests(com.example.grpc.management.GetGuestsRequest request, StreamObserver<com.example.grpc.management.GetGuestsResponse> responseObserver) {
        log.info("gRPC Admin | Запрос списка гостей для отеля: {}", request.getHotelKey());
        List<Guest> guests = guestRepository.findAllByHotelKey(request.getHotelKey());
        com.example.grpc.management.GetGuestsResponse.Builder rb = com.example.grpc.management.GetGuestsResponse.newBuilder();

        guests.forEach(g -> rb.addGuests(com.example.grpc.management.GuestManagementProto.newBuilder()
                .setId(String.valueOf(g.getId()))
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

            Guest guest = (p.getId() == null || p.getId().isEmpty() || "0".equals(p.getId()))
                    ? new Guest()
                    : guestRepository.findById(Long.parseLong(p.getId())).orElse(new Guest());

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

    @Override
    @Transactional(readOnly = true)
    public void getStaff(com.example.grpc.management.GetStaffRequest request, StreamObserver<com.example.grpc.management.GetStaffResponse> responseObserver) {
        log.info("gRPC Admin | Запрос списка персонала для отеля: {}", request.getHotelKey());
        List<HotelStaff> staffList = hotelStaffRepository.findAllByHotelKey(request.getHotelKey());
        com.example.grpc.management.GetStaffResponse.Builder rb = com.example.grpc.management.GetStaffResponse.newBuilder();

        staffList.forEach(s -> rb.addStaff(com.example.grpc.management.StaffManagementProto.newBuilder()
                .setId(String.valueOf(s.getId()))
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

            HotelStaff staff = (p.getId() == null || p.getId().isEmpty() || "0".equals(p.getId()))
                    ? new HotelStaff()
                    : hotelStaffRepository.findById(Long.parseLong(p.getId())).orElse(new HotelStaff());

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

    @Override
    @Transactional(readOnly = true)
    public void getMenu(com.example.grpc.management.GetMenuRequest request, StreamObserver<com.example.grpc.management.GetMenuResponse> responseObserver) {
        log.info("📥 gRPC Admin | Запрос ресторанного меню для отеля: {}", request.getHotelKey());
        List<FoodMenu> items = foodMenuRepository.findAllByHotelKey(request.getHotelKey());
        com.example.grpc.management.GetMenuResponse.Builder rb = com.example.grpc.management.GetMenuResponse.newBuilder();

        items.forEach(m -> rb.addMenuItems(com.example.grpc.management.MenuItemManagementProto.newBuilder()
                .setId(String.valueOf(m.getId()))
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
        log.info("gRPC Admin | Коррекция склада. ID: {}, Остаток: {} шт.", request.getItemId(), request.getStockQuantity());
        try {
            FoodMenu item = foodMenuRepository.findById(Long.parseLong(request.getItemId()))
                    .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));

            item.setStockQuantity(request.getStockQuantity());
            foodMenuRepository.save(item);

            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder().setSuccess(true).setMessage("Складские остатки обновлены").build());
        } catch (Exception e) {
            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder().setSuccess(false).setMessage(e.getMessage()).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    @Transactional(readOnly = true)
    public void getTickets(com.example.grpc.management.GetTicketsRequest request, StreamObserver<com.example.grpc.management.GetTicketsResponse> responseObserver) {
        log.info("gRPC Admin | Запрос лога заявок для отеля: {}", request.getHotelKey());
        List<ActionTicket> tickets = actionTicketRepository.findAllByHotelKeyOrderByCreatedAtDesc(request.getHotelKey());
        com.example.grpc.management.GetTicketsResponse.Builder rb = com.example.grpc.management.GetTicketsResponse.newBuilder();

        tickets.forEach(t -> rb.addTickets(com.example.grpc.management.TicketManagementProto.newBuilder()
                .setId(String.valueOf(t.getId()))
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
        log.info("gRPC Admin | Изменение статуса тикета {} на {}", request.getTicketId(), request.getNewStatus());
        try {
            ActionTicket ticket = actionTicketRepository.findById(Long.parseLong(request.getTicketId()))
                    .orElseThrow(() -> new IllegalArgumentException("Тикет не найден"));

            String oldStatus = ticket.getStatus();
            String newStatus = request.getNewStatus().toUpperCase();
            ticket.setStatus(newStatus);

            if (ticket.getAssignedStaff() != null && ("COMPLETED".equals(newStatus) || "REJECTED".equals(newStatus))) {
                if ("IN_PROGRESS".equals(oldStatus)) {
                    ticket.getAssignedStaff().setStatus("FREE");
                    log.info("Автоматическое освобождение сотрудника: {}", ticket.getAssignedStaff().getName());
                }
            }

            actionTicketRepository.save(ticket);
            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder().setSuccess(true).setMessage("Статус заявки обновлен").build());
        } catch (Exception e) {
            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder().setSuccess(false).setMessage(e.getMessage()).build());
        }
        responseObserver.onCompleted();
    }
    @Override
    @Transactional
    public void deleteStaff(com.example.grpc.management.DeleteStaffRequest request,
                            StreamObserver<com.example.grpc.management.ManagementActionResponse> responseObserver) {
        log.info("gRPC Admin | Удаление сотрудника с ID: {}", request.getStaffId());
        try {
            Long staffId = Long.parseLong(request.getStaffId());
            hotelStaffRepository.deleteById(staffId);
            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder()
                    .setSuccess(true).setMessage("Сотрудник успешно удален из штата").build());
        } catch (Exception e) {
            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder()
                    .setSuccess(false).setMessage(e.getMessage()).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    @Transactional
    public void createMenu(com.example.grpc.management.CreateMenuRequest request,
                           StreamObserver<com.example.grpc.management.ManagementActionResponse> responseObserver) {
        log.info("gRPC Admin | Добавление позиции в меню: {}", request.getName());
        try {
            FoodMenu item = FoodMenu.builder()
                    .hotelKey(request.getHotelKey())
                    .name(request.getName())
                    .price(BigDecimal.valueOf(request.getPrice()))
                    .stockQuantity(request.getStockQuantity())
                    .build();
            foodMenuRepository.save(item);
            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder()
                    .setSuccess(true).setMessage("Позиция успешно добавлена в меню").build());
        } catch (Exception e) {
            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder()
                    .setSuccess(false).setMessage(e.getMessage()).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    @Transactional
    public void deleteMenu(com.example.grpc.management.DeleteMenuRequest request,
                           StreamObserver<com.example.grpc.management.ManagementActionResponse> responseObserver) {
        log.info("📥 gRPC Admin | Удаление позиции меню с ID: {}", request.getItemId());
        try {
            Long itemId = Long.parseLong(request.getItemId());
            foodMenuRepository.deleteById(itemId);
            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder()
                    .setSuccess(true).setMessage("Позиция удалена из каталога").build());
        } catch (Exception e) {
            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder()
                    .setSuccess(false).setMessage(e.getMessage()).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    @Transactional
    public void createTicket(com.example.grpc.management.CreateTicketRequest request,
                             StreamObserver<com.example.grpc.management.ManagementActionResponse> responseObserver) {
        log.info("gRPC Admin | Ручное создание заявки типа {} для комнаты {}", request.getTicketType(), request.getRoomNumber());
        try {
            Guest guest = guestRepository.findAllByHotelKey(request.getHotelKey()).stream()
                    .filter(g -> g.getRoomNumber().equals(request.getRoomNumber()))
                    .findFirst()
                    .orElseGet(() -> guestRepository.save(Guest.builder()
                            .hotelKey(request.getHotelKey())
                            .chatId("manual_session_" + System.currentTimeMillis())
                            .firstName(request.getGuestName())
                            .lastName("")
                            .roomNumber(request.getRoomNumber())
                            .status("CHECKED_IN")
                            .build()));

            ActionTicket ticket = ActionTicket.builder()
                    .hotelKey(request.getHotelKey())
                    .guest(guest)
                    .ticketType(request.getTicketType())
                    .status(request.getStatus().toUpperCase())
                    .totalPrice(BigDecimal.valueOf(request.getTotalPrice()))
                    .build();

            actionTicketRepository.save(ticket);
            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder()
                    .setSuccess(true).setMessage("Заявка успешно зарегистрирована").build());
        } catch (Exception e) {
            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder()
                    .setSuccess(false).setMessage(e.getMessage()).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    @Transactional
    public void deleteTicket(com.example.grpc.management.DeleteTicketRequest request,
                             StreamObserver<com.example.grpc.management.ManagementActionResponse> responseObserver) {
        log.info("gRPC Admin | Удаление тикета с ID: {}", request.getTicketId());
        try {
            Long ticketId = Long.parseLong(request.getTicketId());
            actionTicketRepository.deleteById(ticketId);
            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder()
                    .setSuccess(true).setMessage("Заявка удалена из логов СУБД").build());
        } catch (Exception e) {
            responseObserver.onNext(com.example.grpc.management.ManagementActionResponse.newBuilder()
                    .setSuccess(false).setMessage(e.getMessage()).build());
        }
        responseObserver.onCompleted();
    }
}