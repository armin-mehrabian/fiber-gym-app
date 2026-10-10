package app.fiber.catalog;

import java.util.List;

/**
 * One item of GET /api/exercises. repRange is [low, high]; equipment is empty for bodyweight exercises.
 */
public record ExerciseResponse(
	String id,
	String name,
	List<String> equipment,
	String pattern,
	List<String> primary,
	List<String> secondary,
	List<Integer> repRange,
	String cue) {

	static ExerciseResponse from(Exercise exercise) {
		return new ExerciseResponse(
			exercise.getId(),
			exercise.getName(),
			exercise.sortedEquipmentIds(),
			exercise.getPattern(),
			exercise.regionIds(RegionRole.PRIMARY),
			exercise.regionIds(RegionRole.SECONDARY),
			List.of(exercise.getRepLow(), exercise.getRepHigh()),
			exercise.getCue());
	}

}
