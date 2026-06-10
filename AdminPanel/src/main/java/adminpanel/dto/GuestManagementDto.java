package adminpanel.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuestManagementDto {
    private String id;
    private String chatId;
    private String firstName;
    private String lastName;
    private String roomNumber;
    private String passportDataEncrypted;
    private String status;
}