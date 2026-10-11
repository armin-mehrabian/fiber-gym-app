package app.fiber.recommend;

import java.util.List;

/** One swap option in GET /api/recommendation/alternatives. similarity is 0..1 with 2 decimals. */
public record AlternativeResponse(
	String exerciseId,
	String name,
	List<String> primary,
	List<String> secondary,
	double similarity) {
}
