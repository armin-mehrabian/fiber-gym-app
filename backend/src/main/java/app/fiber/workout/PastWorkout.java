package app.fiber.workout;

import app.fiber.progression.LoggedSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One finished workout and the completed sets one exercise had in it (in the order they were done). */
record PastWorkout(UUID sessionId, Instant startedAt, List<FinishedSetRow> sets) {

	/** The sets that count for training: completed and not warm-ups. */
	List<LoggedSet> workingSets() {
		return sets.stream()
			.filter(set -> !set.warmup())
			.map(FinishedSetRow::toLoggedSet)
			.toList();
	}

	/** Groups rows (all of one exercise) by workout, keeping the order the workouts first appear in. */
	static List<PastWorkout> groupBySession(List<FinishedSetRow> rows) {
		Map<UUID, List<FinishedSetRow>> bySession = new LinkedHashMap<>();
		for (FinishedSetRow row : rows) {
			bySession.computeIfAbsent(row.sessionId(), id -> new ArrayList<>()).add(row);
		}
		return bySession.values().stream()
			.map(sets -> new PastWorkout(sets.getFirst().sessionId(), sets.getFirst().sessionStartedAt(), sets))
			.toList();
	}

}
