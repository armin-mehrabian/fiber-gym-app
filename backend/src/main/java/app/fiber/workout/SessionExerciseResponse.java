package app.fiber.workout;

import app.fiber.progression.SuggestionReason;
import java.math.BigDecimal;
import java.util.List;

/**
 * One exercise of a workout in the API, with the library details the screens need
 * (name, primary and secondary regions in library order; the first primary is the main region).
 *
 * suggestedWeightKg / suggestedReps / suggestionReason were worked out when the exercise was added
 * (null for exercises added before suggestions existed; the weight is also null with NO_HISTORY).
 * lastTime is the last finished workout with this exercise; it is only filled while the workout
 * is in progress, otherwise null.
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
	Integer suggestedReps,
	SuggestionReason suggestionReason,
	LastTimeResponse lastTime,
	List<SetResponse> sets) {
}
