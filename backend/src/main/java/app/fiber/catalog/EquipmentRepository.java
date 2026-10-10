package app.fiber.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentRepository extends JpaRepository<Equipment, String> {

	/** File order; the id breaks ties (e.g. an old row no longer in the file keeps its last position). */
	List<Equipment> findAllByOrderByPositionAscIdAsc();

}
