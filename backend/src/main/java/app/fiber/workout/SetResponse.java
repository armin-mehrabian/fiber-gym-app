package app.fiber.workout;

import java.math.BigDecimal;
import java.util.UUID;

/** One set in the API. weightKg is always in kg (0 for bodyweight). */
public record SetResponse(
	UUID id,
	int setNumber,
	BigDecimal weightKg,
	int reps,
	boolean isWarmup,
	boolean completed) {

	static SetResponse from(WorkoutSet set) {
		return new SetResponse(set.getId(), set.getSetNumber(), set.getWeightKg(), set.getReps(), set.isWarmup(),
			set.isCompleted());
	}

}
