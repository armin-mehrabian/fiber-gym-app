package app.fiber.settings;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SplitDayRepository extends JpaRepository<SplitDay, UUID> {

	List<SplitDay> findByUserIdOrderByPositionAsc(UUID userId);

}
