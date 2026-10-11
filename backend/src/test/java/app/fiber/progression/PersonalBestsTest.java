package app.fiber.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class PersonalBestsTest {

	@Test
	void theEstimatedOneRepMaxIsEpley() {
		assertThat(PersonalBests.estimatedOneRepMax(set("60", 10))).isEqualTo(80.0);
		assertThat(PersonalBests.estimatedOneRepMax(set("100", 0))).isEqualTo(100.0);
		assertThat(PersonalBests.estimatedOneRepMax(set("0", 20))).isZero();
	}

	@Test
	void theHigherEstimateIsTheBetterSet() {
		// 100 x 5 = 116.7, 110 x 1 = 113.7: more reps at a bit less weight wins.
		assertThat(PersonalBests.compare(set("100", 5), set("110", 1))).isPositive();
		assertThat(PersonalBests.compare(set("110", 1), set("100", 5))).isNegative();
	}

	@Test
	void equalEstimatesAreEqualExactly() {
		// 90 x 10 and 100 x 6 are both exactly 120; floating point must not pick one.
		assertThat(PersonalBests.compare(set("90", 10), set("100", 6))).isZero();
		assertThat(PersonalBests.compare(set("60", 10), set("60.00", 10))).isZero();
	}

	@Test
	void bodyweightSetsCompareByReps() {
		assertThat(PersonalBests.compare(set("0", 16), set("0", 15))).isPositive();
		assertThat(PersonalBests.compare(set("0", 15), set("0", 15))).isZero();
		// Any added weight beats bodyweight.
		assertThat(PersonalBests.compare(set("2.5", 1), set("0", 50))).isPositive();
	}

	@Test
	void theBestOfAListIsTheFirstOfTheBestOnATie() {
		List<LoggedSet> sets = List.of(set("50", 8), set("90", 10), set("100", 6), set("80", 12));

		assertThat(PersonalBests.best(sets, Function.identity())).containsSame(sets.get(1));
		assertThat(PersonalBests.best(List.<LoggedSet>of(), Function.identity())).isEmpty();
	}

	@Test
	void beatingEveryEarlierWorkoutIsAPersonalBest() {
		List<LoggedSet> earlier = List.of(set("60", 10), set("60", 9), set("55", 12));

		assertThat(PersonalBests.isPersonalBest(List.of(set("62.5", 8), set("60", 11)), earlier)).isTrue();
	}

	@Test
	void matchingTheBestIsNotAPersonalBest() {
		List<LoggedSet> earlier = List.of(set("60", 10));

		assertThat(PersonalBests.isPersonalBest(List.of(set("60", 10)), earlier)).isFalse();
		assertThat(PersonalBests.isPersonalBest(List.of(set("57.5", 10)), earlier)).isFalse();
	}

	@Test
	void aFirstAttemptIsNotAPersonalBest() {
		assertThat(PersonalBests.isPersonalBest(List.of(set("100", 5)), List.of())).isFalse();
	}

	@Test
	void noWorkingSetsIsNotAPersonalBest() {
		assertThat(PersonalBests.isPersonalBest(List.of(), List.of(set("100", 5)))).isFalse();
	}

	@Test
	void bodyweightPersonalBestsAreAboutReps() {
		List<LoggedSet> earlier = List.of(set("0", 15), set("0", 12));

		assertThat(PersonalBests.isPersonalBest(List.of(set("0", 16)), earlier)).isTrue();
		assertThat(PersonalBests.isPersonalBest(List.of(set("0", 15)), earlier)).isFalse();
	}

	private static LoggedSet set(String weightKg, int reps) {
		return new LoggedSet(new BigDecimal(weightKg), reps);
	}

}
