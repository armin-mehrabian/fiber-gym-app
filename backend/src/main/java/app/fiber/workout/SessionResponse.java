package app.fiber.workout;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A workout in the API. finishedAt is null while the workout is ACTIVE. */
public record SessionResponse(
	UUID id,
	String name,
	SessionStatus status,
	Instant startedAt,
	Instant finishedAt,
	List<SessionExerciseResponse> exercises) {
}
