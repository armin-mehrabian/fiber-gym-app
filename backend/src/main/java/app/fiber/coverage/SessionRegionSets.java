package app.fiber.coverage;

import app.fiber.catalog.RegionRole;
import java.time.Instant;
import java.util.UUID;

/**
 * One database row for recovery: in this finished workout, {@code sets} counted sets were done
 * for exercises that have this region with this role.
 */
public record SessionRegionSets(
	UUID sessionId,
	Instant finishedAt,
	String regionId,
	RegionRole role,
	long sets) {
}
