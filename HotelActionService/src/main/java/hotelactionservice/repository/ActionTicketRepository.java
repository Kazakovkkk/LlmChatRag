package hotelactionservice.repository;

import hotelactionservice.entity.ActionTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ActionTicketRepository extends JpaRepository<ActionTicket, Long> {
    List<ActionTicket> findAllByHotelKeyOrderByCreatedAtDesc(String hotelKey);
}