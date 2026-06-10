package hotelactionservice.repository;

import hotelactionservice.entity.HotelStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface HotelStaffRepository extends JpaRepository<HotelStaff, UUID> {
    // Найти первого доступного (свободного) сотрудника определенной роли для конкретного отеля
    List<HotelStaff> findByHotelKeyAndRoleAndStatus(String hotelKey, String role, String status);
    List<hotelactionservice.entity.HotelStaff> findAllByHotelKey(String hotelKey);
}