package hotelactionservice.repository;

import hotelactionservice.entity.FoodMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FoodMenuRepository extends JpaRepository<FoodMenu, Long> {
    Optional<FoodMenu> findByHotelKeyAndNameIgnoreCase(String hotelKey, String name);
    List<FoodMenu> findAllByHotelKey(String hotelKey);
}