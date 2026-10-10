package app.fiber.workout;

/**
 * ACTIVE = the workout is in progress and can be changed.
 * FINISHED = done; it counts towards history and can no longer be changed.
 * There is no "abandoned" state: discarding a workout deletes it.
 */
public enum SessionStatus {
	ACTIVE,
	FINISHED
}
