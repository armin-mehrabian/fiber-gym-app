package app.fiber.coverage;

import java.util.List;

/**
 * One region in GET /api/coverage/week. effective has at most 1 decimal, coverage is 0..1 with
 * 2 decimals, state is NOT_YET, STARTED or DONE. recovering doesn't depend on the week asked for:
 * it is about the last 48 hours.
 */
public record RegionCoverageResponse(
	String regionId,
	double effective,
	int target,
	double coverage,
	RegionState state,
	boolean recovering,
	List<ContributionResponse> contributions) {
}
