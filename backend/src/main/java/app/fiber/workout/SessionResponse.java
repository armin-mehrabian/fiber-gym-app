package app.fiber.workout;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A workout in the API. splitDayId is null for a workout started without a split day (or whose
 * day was deleted later). finishedAt is null while the workout is ACTIVE.
 */
public record SessionResponse(
	UUID id,
	String name,
	UUID splitDayId,
	SessionStatus status,
	Instant startedAt,
	Instant finishedAt,
	List<SessionExerciseResponse> exercises) {
}
