package hotelactionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "hotel_staff")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotelStaff {

    @Id
    private UUID id;

    @Column(name = "hotel_key", nullable = false, length = 50)
    private String hotelKey;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 50)
    private String role; // 'MAID', 'WAITER', 'TECHNICIAN'

    @Column(nullable = false, length = 30)
    private String status; // 'FREE', 'BUSY', 'OFF_DUTY'
}