package hotelactionservice.repository;

import hotelactionservice.entity.ActionTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActionTicketRepository extends JpaRepository<ActionTicket, UUID> {
    // Выгружаем тикеты, сортируя: новые транзакции всегда первыми в списке
    List<ActionTicket> findAllByHotelKeyOrderByCreatedAtDesc(String hotelKey);
}