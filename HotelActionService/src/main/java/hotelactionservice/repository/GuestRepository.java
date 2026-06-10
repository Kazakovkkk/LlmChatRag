package hotelactionservice.repository;

import hotelactionservice.entity.Guest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GuestRepository extends JpaRepository<Guest, UUID> {
    // Найти гостя по его активной сессии в чате и ключу отеля (SaaS изоляция)
    Optional<Guest> findByChatIdAndHotelKey(String chatId, String hotelKey);
    List<Guest> findAllByHotelKey(String hotelKey);
}