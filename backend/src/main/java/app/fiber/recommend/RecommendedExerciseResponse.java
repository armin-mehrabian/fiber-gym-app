package app.fiber.recommend;

import app.fiber.progression.SuggestionReason;
import java.math.BigDecimal;
import java.util.List;

/** One exercise of the recommended workout: what it hits, the plan (sets, rep range) and the suggestion. */
public record RecommendedExerciseResponse(
	String exerciseId,
	String name,
	List<String> primary,
	List<String> secondary,
	int sets,
	List<Integer> repRange,
	BigDecimal suggestedWeightKg,
	Integer suggestedReps,
	SuggestionReason suggestionReason) {
}
