package app.fiber.workout;

import app.fiber.progression.LoggedSet;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One completed set (warm-up or working set) of an exercise in one of the user's finished workouts. */
public record FinishedSetRow(
	String exerciseId,
	UUID sessionId,
	Instant sessionStartedAt,
	BigDecimal weightKg,
	int reps,
	boolean warmup) {

	LoggedSet toLoggedSet() {
		return new LoggedSet(weightKg, reps);
	}

}
