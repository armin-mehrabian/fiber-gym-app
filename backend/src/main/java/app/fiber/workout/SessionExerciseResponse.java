package app.fiber.workout;

import java.math.BigDecimal;
import java.util.List;

/**
 * One exercise of a workout in the API, with the library details the screens need
 * (name, primary and secondary regions in library order; the first primary is the main region).
 */
public record SessionExerciseResponse(
	int position,
	String exerciseId,
	String name,
	List<String> primary,
	List<String> secondary,
	int targetSets,
	List<Integer> repRange,
	BigDecimal suggestedWeightKg,
	List<SetResponse> sets) {
}
