package hotelactionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "action_tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActionTicket {

    @Id
    private UUID id;

    @Column(name = "hotel_key", nullable = false, length = 50)
    private String hotelKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;

    @Column(name = "ticket_type", nullable = false, length = 50)
    private String ticketType; // 'ORDER_FOOD', 'ROOM_CLEANING'

    @Column(nullable = false, length = 30)
    private String status; // 'CREATED', 'IN_PROGRESS', 'QUEUED', 'COMPLETED', 'REJECTED'

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_staff_id")
    private HotelStaff assignedStaff;

    @Column(name = "total_price", precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}