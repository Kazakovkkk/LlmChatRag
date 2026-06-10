package adminpanel.grpc;

import com.example.grpc.management.*;
import com.example.grpc.management.HotelManagementServiceGrpc; // Явный импорт!
import adminpanel.dto.*;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class HotelManagementGrpcClient {

    private final HotelManagementServiceGrpc.HotelManagementServiceBlockingStub stub;

    public HotelManagementGrpcClient(GrpcChannelFactory channelFactory) {
        // Подключаемся к каналу из application.properties
        ManagedChannel channel = channelFactory.createChannel("hotel-management-service");
        this.stub = HotelManagementServiceGrpc.newBlockingStub(channel);
    }

    // 1. Получение и обновление Гостей
    public List<GuestManagementDto> fetchGuests(String hotelKey) {
        GetGuestsResponse response = stub.getGuests(GetGuestsRequest.newBuilder().setHotelKey(hotelKey).build());
        List<GuestManagementDto> dtos = new ArrayList<>();
        response.getGuestsList().forEach(p -> dtos.add(new GuestManagementDto(
                p.getId(), p.getChatId(), p.getFirstName(), p.getLastName(), p.getRoomNumber(), p.getPassportDataEncrypted(), p.getStatus()
        )));
        return dtos;
    }

    public boolean updateGuest(String hotelKey, GuestManagementDto dto) {
        GuestManagementProto proto = GuestManagementProto.newBuilder()
                .setId(dto.getId()).setChatId(dto.getChatId()).setFirstName(dto.getFirstName())
                .setLastName(dto.getLastName()).setRoomNumber(dto.getRoomNumber())
                .setPassportDataEncrypted(dto.getPassportDataEncrypted()).setStatus(dto.getStatus()).build();

        ManagementActionResponse res = stub.updateGuest(UpdateGuestRequest.newBuilder().setHotelKey(hotelKey).setGuest(proto).build());
        return res.getSuccess();
    }

    // 2. Получение и обновление Персонала
    public List<StaffManagementDto> fetchStaff(String hotelKey) {
        GetStaffResponse response = stub.getStaff(GetStaffRequest.newBuilder().setHotelKey(hotelKey).build());
        List<StaffManagementDto> dtos = new ArrayList<>();
        response.getStaffList().forEach(p -> dtos.add(new StaffManagementDto(p.getId(), p.getName(), p.getRole(), p.getStatus())));
        return dtos;
    }

    public boolean updateStaff(String hotelKey, StaffManagementDto dto) {
        StaffManagementProto proto = StaffManagementProto.newBuilder()
                .setId(dto.getId()).setName(dto.getName()).setRole(dto.getRole()).setStatus(dto.getStatus()).build();

        ManagementActionResponse res = stub.updateStaff(UpdateStaffRequest.newBuilder().setHotelKey(hotelKey).setStaff(proto).build());
        return res.getSuccess();
    }

    // 3. Получение и обновление Склада еды
    public List<MenuItemManagementDto> fetchMenu(String hotelKey) {
        GetMenuResponse response = stub.getMenu(GetMenuRequest.newBuilder().setHotelKey(hotelKey).build());
        List<MenuItemManagementDto> dtos = new ArrayList<>();
        response.getMenuItemsList().forEach(p -> dtos.add(new MenuItemManagementDto(p.getId(), p.getName(), p.getPrice(), p.getStockQuantity())));
        return dtos;
    }

    public boolean updateMenuStock(String hotelKey, String itemId, int stockQuantity) {
        UpdateMenuStockRequest req = UpdateMenuStockRequest.newBuilder()
                .setHotelKey(hotelKey).setItemId(itemId).setStockQuantity(stockQuantity).build();
        ManagementActionResponse res = stub.updateMenuStock(req);
        return res.getSuccess();
    }

    // 4. Получение и обновление Статуса заявок ИИ
    public List<TicketManagementDto> fetchTickets(String hotelKey) {
        GetTicketsResponse response = stub.getTickets(GetTicketsRequest.newBuilder().setHotelKey(hotelKey).build());
        List<TicketManagementDto> dtos = new ArrayList<>();
        response.getTicketsList().forEach(p -> dtos.add(new TicketManagementDto(
                p.getId(), p.getGuestName(), p.getRoomNumber(), p.getTicketType(), p.getStatus(), p.getAssignedStaffName(), p.getTotalPrice(), p.getCreatedAt()
        )));
        return dtos;
    }

    public boolean updateTicketStatus(String ticketId, String newStatus) {
        UpdateTicketStatusRequest req = UpdateTicketStatusRequest.newBuilder()
                .setTicketId(ticketId).setNewStatus(newStatus).build();
        ManagementActionResponse res = stub.updateTicketStatus(req);
        return res.getSuccess();
    }
}