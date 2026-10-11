package app.fiber.workout;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GET /api/sessions/{id}/summary: what a finished workout did.
 * regionsHit = region id to 1 (hit mainly) or 0.5 (helped), for exercises with at least one working set.
 * weekPercentBefore / weekPercentAfter = that week's coverage without and with this workout (0-100).
 */
public record SummaryResponse(
	UUID sessionId,
	String name,
	long durationMinutes,
	int workingSets,
	Map<String, Double> regionsHit,
	int weekPercentBefore,
	int weekPercentAfter,
	List<PersonalBestResponse> personalBests) {
}
