package app.fiber.coverage;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/coverage/week. weekStart is the Monday and weekEnd the Sunday, e.g. "2026-10-05" and "2026-10-11".
 * days has 7 entries (Monday to Sunday); regions has every region, in region order.
 */
public record CoverageWeekResponse(
	LocalDate weekStart,
	LocalDate weekEnd,
	int weekPercent,
	List<CoverageDayResponse> days,
	List<RegionCoverageResponse> regions) {
}
