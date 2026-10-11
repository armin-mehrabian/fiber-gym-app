package app.fiber.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The recovery rule, without Spring or a database. */
class RecoveryCalculatorTest {

	private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

	@Test
	void exactlyThreeEffectiveSetsInOneWorkoutIsRecovering() {
		RecentSession session = finishedHoursAgo(2, Map.of("chest_mid", 3.0));

		assertThat(RecoveryCalculator.recoveringRegions(List.of(session), NOW)).containsExactly("chest_mid");
	}

	@Test
	void twoAndAHalfEffectiveSetsIsNotRecovering() {
		RecentSession session = finishedHoursAgo(2, Map.of("delt_front", 2.5));

		assertThat(RecoveryCalculator.recoveringRegions(List.of(session), NOW)).isEmpty();
	}

	@Test
	void twoLightWorkoutsDoNotAddUp() {
		// 2 + 2 = 4 sets in 48 hours, but no single workout reached 3.
		List<RecentSession> sessions = List.of(
			finishedHoursAgo(30, Map.of("chest_mid", 2.0)),
			finishedHoursAgo(5, Map.of("chest_mid", 2.0)));

		assertThat(RecoveryCalculator.recoveringRegions(sessions, NOW)).isEmpty();
	}

	@Test
	void aHardWorkout47HoursAgoStillCounts() {
		RecentSession session = finishedHoursAgo(47, Map.of("lats_lower", 4.0));

		assertThat(RecoveryCalculator.recoveringRegions(List.of(session), NOW)).containsExactly("lats_lower");
	}

	@Test
	void aHardWorkout49HoursAgoNoLongerCounts() {
		RecentSession session = finishedHoursAgo(49, Map.of("lats_lower", 4.0));

		assertThat(RecoveryCalculator.recoveringRegions(List.of(session), NOW)).isEmpty();
	}

	@Test
	void exactly48HoursAgoIsStillInsideTheWindow() {
		RecentSession onTheEdge = new RecentSession(NOW.minus(Duration.ofHours(48)), Map.of("calves", 3.0));
		RecentSession justOutside = new RecentSession(NOW.minus(Duration.ofHours(48)).minusMillis(1),
			Map.of("glutes", 3.0));

		assertThat(RecoveryCalculator.recoveringRegions(List.of(onTheEdge, justOutside), NOW))
			.containsExactly("calves");
	}

	@Test
	void onlyTheHardRegionsOfAWorkoutAreRecovering() {
		// e.g. 3 sets of incline press: 3 for both chest regions, 1.5 for the front delts.
		RecentSession session = finishedHoursAgo(10, Map.of("chest_upper", 3.0, "chest_mid", 3.0, "delt_front", 1.5));

		assertThat(RecoveryCalculator.recoveringRegions(List.of(session), NOW))
			.containsExactly("chest_mid", "chest_upper");
	}

	@Test
	void regionsFromSeveralWorkoutsAreCombined() {
		List<RecentSession> sessions = List.of(
			finishedHoursAgo(40, Map.of("chest_mid", 3.0)),
			finishedHoursAgo(3, Map.of("lats_lower", 3.0, "chest_mid", 1.0)));

		assertThat(RecoveryCalculator.recoveringRegions(sessions, NOW)).containsExactly("chest_mid", "lats_lower");
	}

	@Test
	void noRecentWorkoutsMeansNothingIsRecovering() {
		assertThat(RecoveryCalculator.recoveringRegions(List.of(), NOW)).isEmpty();
	}

	@Test
	void theWindowStarts48HoursBeforeNow() {
		assertThat(RecoveryCalculator.windowStart(NOW)).isEqualTo(Instant.parse("2026-10-08T12:00:00Z"));
	}

	private static RecentSession finishedHoursAgo(int hours, Map<String, Double> effectiveSetsByRegion) {
		return new RecentSession(NOW.minus(Duration.ofHours(hours)), effectiveSetsByRegion);
	}

}
