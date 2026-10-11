package app.fiber.workout;

import static org.assertj.core.api.Assertions.assertThat;

import app.fiber.catalog.Exercise;
import app.fiber.catalog.ExerciseRegion;
import app.fiber.catalog.RegionRole;
import app.fiber.progression.LoggedSet;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The summary's small rules without a database. The endpoint is tested end to end in SummaryIntegrationTest. */
class SummaryServiceTest {

	private static final Instant START = Instant.parse("2026-10-05T17:00:00Z");

	@Test
	void durationIsRoundedToWholeMinutes() {
		assertThat(SummaryService.durationMinutes(START, START.plusSeconds(45 * 60 + 29))).isEqualTo(45);
		assertThat(SummaryService.durationMinutes(START, START.plusSeconds(45 * 60 + 30))).isEqualTo(46);
		assertThat(SummaryService.durationMinutes(START, START)).isZero();
	}

	@Test
	void onlyCompletedNonWarmupSetsAreWorkingSetsAndRepeatedExercisesAreMerged() {
		WorkoutSession session = new WorkoutSession(UUID.randomUUID(), null, "Workout", START);
		SessionExercise press = session.addExercise("press", 3, 6, 10);
		press.addSet(kg("20"), 10, true, START).setCompleted(true); // warm-up
		press.addSet(kg("60"), 8, false, START).setCompleted(true);
		press.addSet(kg("60"), 7, false, START); // never ticked
		SessionExercise flyNotDone = session.addExercise("fly", 3, 10, 15);
		flyNotDone.addSet(kg("10"), 12, false, START);
		SessionExercise pressAgain = session.addExercise("press", 3, 6, 10);
		pressAgain.addSet(kg("50"), 10, false, START).setCompleted(true);

		Map<String, List<LoggedSet>> sets = SummaryService.workingSetsByExercise(session);

		assertThat(sets).containsOnlyKeys("press");
		assertThat(sets.get("press")).containsExactly(new LoggedSet(kg("60"), 8), new LoggedSet(kg("50"), 10));
	}

	@Test
	void regionsHitArePrimaryOneSecondaryHalfAndPrimaryWins() {
		Exercise incline = exercise("incline", Map.of("chest_upper", RegionRole.PRIMARY,
			"chest_mid", RegionRole.PRIMARY, "delt_front", RegionRole.SECONDARY));
		Exercise raise = exercise("raise", Map.of("delt_front", RegionRole.PRIMARY, "delt_side", RegionRole.SECONDARY));

		assertThat(SummaryService.regionsHit(List.of(incline, raise))).containsExactlyInAnyOrderEntriesOf(Map.of(
			"chest_mid", 1.0, "chest_upper", 1.0, "delt_front", 1.0, "delt_side", 0.5));
		assertThat(SummaryService.regionsHit(List.of())).isEmpty();
	}

	/** With 2 decimals, the way sets keep their weight. */
	private static BigDecimal kg(String value) {
		return new BigDecimal(value).setScale(2);
	}

	private static Exercise exercise(String id, Map<String, RegionRole> regions) {
		Set<ExerciseRegion> links = regions.entrySet().stream()
			.map(entry -> new ExerciseRegion(entry.getKey(), entry.getValue(), 0))
			.collect(Collectors.toSet());
		return new Exercise(id, id, "x", 8, 12, "Cue.", null, false, Set.of(), links);
	}

}
