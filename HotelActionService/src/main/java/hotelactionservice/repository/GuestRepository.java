package hotelactionservice.repository;

import hotelactionservice.entity.Guest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface GuestRepository extends JpaRepository<Guest, Long> {
    Optional<Guest> findByChatIdAndHotelKey(String chatId, String hotelKey);
    List<Guest> findAllByHotelKey(String hotelKey);
}