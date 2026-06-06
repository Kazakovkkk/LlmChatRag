package adminpanel.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uc_users_username", columnNames = {"username"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username; // Логин админа (email)

    @Column(nullable = false)
    private String passwordHash; // Захэшированный пароль

    @Column(nullable = false)
    private String hotelKey; // К какому отелю привязан этот админ

    @Column(nullable = false)
    private String role; // ROLE_ADMIN, ROLE_SUPERADMIN
}