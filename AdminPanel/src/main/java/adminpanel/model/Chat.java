package adminpanel.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chats", indexes = {
        @Index(name = "idx_chats_hotel", columnList = "hotelKey")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chat {
    @Id
    private String id; // Сгенерированный UUID или сессия с фронтенда гостя

    @Column(nullable = false)
    private String hotelKey;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("timestamp ASC")
    @Builder.Default
    @JsonManagedReference
    private List<Message> messages = new ArrayList<>();

}