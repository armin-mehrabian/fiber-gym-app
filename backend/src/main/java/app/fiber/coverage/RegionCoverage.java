package app.fiber.coverage;

import java.util.List;

/**
 * One region's week: effective sets (1 decimal), the user's target, coverage 0..1 (2 decimals),
 * its state in words (not yet / started / done), and the exercises that contributed, sorted by day
 * and then exercise name.
 */
public record RegionCoverage(
	String regionId,
	double effective,
	int target,
	double coverage,
	RegionState state,
	List<Contribution> contributions) {
}
