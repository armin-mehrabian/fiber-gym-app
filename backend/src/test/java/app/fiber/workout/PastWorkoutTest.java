package app.fiber.workout;

import static org.assertj.core.api.Assertions.assertThat;

import app.fiber.progression.LoggedSet;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Grouping an exercise's past sets by workout, and finding "last time". */
class PastWorkoutTest {

	private static final UUID NEWER = UUID.randomUUID();
	private static final UUID OLDER = UUID.randomUUID();
	private static final Instant NEWER_START = Instant.parse("2026-10-08T17:00:00Z");
	private static final Instant OLDER_START = Instant.parse("2026-10-05T17:00:00Z");

	@Test
	void rowsAreGroupedByWorkoutInTheOrderGiven() {
		List<PastWorkout> workouts = PastWorkout.groupBySession(List.of(
			row(NEWER, NEWER_START, "60", 8, false), row(NEWER, NEWER_START, "60", 7, false),
			row(OLDER, OLDER_START, "57.5", 10, false)));

		assertThat(workouts).extracting(PastWorkout::sessionId).containsExactly(NEWER, OLDER);
		assertThat(workouts.getFirst().workingSets())
			.containsExactly(new LoggedSet(new BigDecimal("60"), 8), new LoggedSet(new BigDecimal("60"), 7));
	}

	@Test
	void warmupsAreNotWorkingSets() {
		PastWorkout workout = PastWorkout.groupBySession(List.of(
			row(NEWER, NEWER_START, "20", 10, true), row(NEWER, NEWER_START, "60", 8, false))).getFirst();

		assertThat(workout.workingSets()).containsExactly(new LoggedSet(new BigDecimal("60"), 8));
	}

	@Test
	void lastTimeSkipsAWorkoutWithOnlyWarmups() {
		List<PastWorkout> workouts = PastWorkout.groupBySession(List.of(
			row(NEWER, NEWER_START, "20", 10, true), row(OLDER, OLDER_START, "57.5", 10, false)));

		assertThat(ExerciseHistoryService.lastWorkout(workouts)).get()
			.extracting(PastWorkout::sessionId).isEqualTo(OLDER);
	}

	@Test
	void noWorkoutsMeansNoLastTime() {
		assertThat(ExerciseHistoryService.lastWorkout(List.of())).isEmpty();
	}

	private static FinishedSetRow row(UUID sessionId, Instant start, String weightKg, int reps, boolean warmup) {
		return new FinishedSetRow("press", sessionId, start, new BigDecimal(weightKg), reps, warmup);
	}

}
