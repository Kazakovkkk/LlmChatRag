package hotelactionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "guests", indexes = {
        @Index(name = "idx_guests_chat_hotel", columnList = "chat_id, hotel_key")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Guest {

    @Id
    private UUID id;

    @Column(name = "hotel_key", nullable = false, length = 50)
    private String hotelKey;

    @Column(name = "chat_id", nullable = false, length = 100)
    private String chatId;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "room_number", length = 20)
    private String roomNumber;

    @Column(name = "passport_data_encrypted", columnDefinition = "TEXT")
    private String passportDataEncrypted;

    @Column(nullable = false, length = 30)
    private String status; // 'CHECKED_IN', 'CHECKED_OUT'
}