package app.fiber.settings;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RegionTargetRepository extends JpaRepository<RegionTarget, RegionTargetId> {

	/** All targets of one user, in the order regions are shown (region.sort_order). */
	@Query("""
		select t from RegionTarget t join Region r on r.id = t.id.regionId
		where t.id.userId = :userId
		order by r.sortOrder""")
	List<RegionTarget> findByUserInRegionOrder(UUID userId);

}
