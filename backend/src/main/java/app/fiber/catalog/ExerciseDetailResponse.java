package app.fiber.catalog;

import app.fiber.workout.BestSetResponse;
import app.fiber.workout.LastTimeResponse;
import java.util.List;

/**
 * Body of GET /api/exercises/{id}: the list item fields plus note, best and last.
 * best (the user's best set ever) and last (their last finished workout with it) are null for
 * visitors, and for a user who hasn't done the exercise yet.
 */
public record ExerciseDetailResponse(
	String id,
	String name,
	List<String> equipment,
	String pattern,
	List<String> primary,
	List<String> secondary,
	List<Integer> repRange,
	String cue,
	String note,
	BestSetResponse best,
	LastTimeResponse last) {

	static ExerciseDetailResponse from(Exercise exercise, BestSetResponse best, LastTimeResponse last) {
		ExerciseResponse item = ExerciseResponse.from(exercise);
		return new ExerciseDetailResponse(item.id(), item.name(), item.equipment(), item.pattern(), item.primary(),
			item.secondary(), item.repRange(), item.cue(), exercise.getNote(), best, last);
	}

}
