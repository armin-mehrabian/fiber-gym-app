package app.fiber.catalog;

import java.util.List;

/**
 * Body of GET /api/exercises/{id}: the list item fields plus note, best and last.
 * best and last (personal best and last workout) are always null until Phase 10 fills them.
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
	Object best,
	Object last) {

	static ExerciseDetailResponse from(Exercise exercise) {
		ExerciseResponse item = ExerciseResponse.from(exercise);
		return new ExerciseDetailResponse(item.id(), item.name(), item.equipment(), item.pattern(), item.primary(),
			item.secondary(), item.repRange(), item.cue(), exercise.getNote(), null, null);
	}

}
