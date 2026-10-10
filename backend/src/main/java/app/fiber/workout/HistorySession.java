package app.fiber.workout;

import java.time.Instant;
import java.util.UUID;

/**
 * One finished workout in GET /api/history.
 * workingSets = completed sets that are not warm-ups (the ones that count for training).
 */
public record HistorySession(
	UUID id,
	String name,
	Instant startedAt,
	Instant finishedAt,
	long exerciseCount,
	long workingSets) {
}
