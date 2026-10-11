package app.fiber.recommend;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GET /api/recommendation/next: the next split day and the exercises that fill this week's gaps
 * (empty when the week is already covered). planned = region id to 1 (hit mainly) or 0.5 (helped).
 */
public record RecommendationResponse(
	UUID splitDayId,
	String name,
	List<RecommendedExerciseResponse> exercises,
	Map<String, Double> planned) {
}
