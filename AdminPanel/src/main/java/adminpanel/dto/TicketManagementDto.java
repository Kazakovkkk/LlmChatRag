package adminpanel.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketManagementDto {
    private String id;
    private String guestName;
    private String roomNumber;
    private String ticketType;
    private String status;
    private String assignedStaffName;
    private double totalPrice;
    private String createdAt;
}