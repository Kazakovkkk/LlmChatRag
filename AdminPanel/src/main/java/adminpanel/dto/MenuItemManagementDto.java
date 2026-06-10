package adminpanel.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemManagementDto {
    private String id;
    private String name;
    private double price;
    private int stockQuantity;
}