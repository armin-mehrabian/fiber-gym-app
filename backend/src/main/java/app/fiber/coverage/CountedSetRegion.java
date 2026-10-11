package app.fiber.coverage;

import app.fiber.catalog.RegionRole;
import java.time.Instant;
import java.util.UUID;

/**
 * One database row for coverage: one counted set and one region its exercise emphasises.
 * A set of an exercise with three regions comes back as three rows with the same setId.
 */
public record CountedSetRegion(
	UUID setId,
	String exerciseName,
	Instant sessionStartedAt,
	String regionId,
	RegionRole role) {
}
