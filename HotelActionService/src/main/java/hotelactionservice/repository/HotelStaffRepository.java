package hotelactionservice.repository;

import hotelactionservice.entity.HotelStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface HotelStaffRepository extends JpaRepository<HotelStaff, Long> {
    List<HotelStaff> findByHotelKeyAndRoleAndStatus(String hotelKey, String role, String status);
    List<HotelStaff> findAllByHotelKey(String hotelKey);
}