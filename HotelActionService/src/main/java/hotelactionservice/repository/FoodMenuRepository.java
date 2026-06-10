package hotelactionservice.repository;

import hotelactionservice.entity.FoodMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FoodMenuRepository extends JpaRepository<FoodMenu, UUID> {
    // Найти позицию в меню отеля по названию (для проверки остатков пиццы/колы)
    Optional<FoodMenu> findByHotelKeyAndNameIgnoreCase(String hotelKey, String name);
    List<FoodMenu> findAllByHotelKey(String hotelKey);
}