package app.fiber.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import app.fiber.catalog.RegionRole;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The coverage maths, without Spring or a database. */
class CoverageCalculatorTest {

	private static final LocalDate MON = LocalDate.of(2026, 10, 5);
	private static final LocalDate TUE = MON.plusDays(1);
	private static final LocalDate THU = MON.plusDays(3);

	// ---- counting sets ----

	@Test
	void aSetGivesOneToEachPrimaryAndHalfToEachSecondaryRegion() {
		CoverageResult result = CoverageCalculator.calculate(
			targets("chest_upper", 6, "chest_mid", 6, "delt_front", 4),
			List.of(set("Incline press", TUE, List.of("chest_upper", "chest_mid"), List.of("delt_front"))));

		assertThat(region(result, "chest_upper").effective()).isEqualTo(1.0);
		assertThat(region(result, "chest_mid").effective()).isEqualTo(1.0);
		assertThat(region(result, "delt_front").effective()).isEqualTo(0.5);
	}

	@Test
	void threeSetsOfOneExerciseAddUp() {
		CoverageResult result = CoverageCalculator.calculate(targets("lats_lower", 6, "biceps_long", 4),
			times(3, set("Single-arm cable row", TUE, List.of("lats_lower"), List.of("biceps_long"))));

		assertThat(region(result, "lats_lower").effective()).isEqualTo(3.0);
		assertThat(region(result, "biceps_long").effective()).isEqualTo(1.5);
	}

	@Test
	void differentExercisesHittingTheSameRegionAddUp() {
		List<CountedSet> sets = new ArrayList<>();
		sets.addAll(times(3, set("Incline press", TUE, List.of("chest_mid"), List.of("delt_front"))));
		sets.addAll(times(2, set("Push-up", TUE, List.of("chest_mid"), List.of("delt_front"))));
		sets.addAll(times(2, set("Overhead press", THU, List.of("delt_front"), List.of())));

		CoverageResult result = CoverageCalculator.calculate(targets("chest_mid", 6, "delt_front", 4), sets);

		assertThat(region(result, "chest_mid").effective()).isEqualTo(5.0);
		// 3 x 0.5 + 2 x 0.5 + 2 x 1
		assertThat(region(result, "delt_front").effective()).isEqualTo(4.5);
	}

	@Test
	void regionsWithoutATargetAreIgnored() {
		CoverageResult result = CoverageCalculator.calculate(targets("chest_mid", 6),
			List.of(set("Incline press", TUE, List.of("chest_mid"), List.of("not_a_region"))));

		assertThat(result.regions()).extracting(RegionCoverage::regionId).containsExactly("chest_mid");
	}

	// ---- coverage ----

	@Test
	void coverageIsEffectiveDividedByTarget() {
		CoverageResult result = CoverageCalculator.calculate(targets("lats_lower", 6),
			times(3, set("Row", TUE, List.of("lats_lower"), List.of())));

		assertThat(region(result, "lats_lower").coverage()).isEqualTo(0.5);
		assertThat(region(result, "lats_lower").target()).isEqualTo(6);
	}

	@Test
	void coverageNeverGoesAboveOne() {
		CoverageResult result = CoverageCalculator.calculate(targets("chest_mid", 6),
			times(10, set("Push-up", TUE, List.of("chest_mid"), List.of())));

		assertThat(region(result, "chest_mid").effective()).isEqualTo(10.0);
		assertThat(region(result, "chest_mid").coverage()).isEqualTo(1.0);
	}

	@Test
	void coverageIsRoundedToTwoDecimals() {
		CoverageResult result = CoverageCalculator.calculate(targets("lats_lower", 6, "chest_mid", 6, "biceps_long", 4),
			List.of(
				set("Row", TUE, List.of("lats_lower"), List.of()), // 1 of 6 = 0.1666...
				set("Push-up", TUE, List.of("chest_mid"), List.of()),
				set("Push-up", TUE, List.of("chest_mid"), List.of()),
				set("Push-up", TUE, List.of("chest_mid"), List.of()),
				set("Push-up", TUE, List.of("chest_mid"), List.of()), // 4 of 6 = 0.666...
				set("Row", TUE, List.of(), List.of("biceps_long")),
				set("Row", TUE, List.of(), List.of("biceps_long")),
				set("Row", TUE, List.of(), List.of("biceps_long")))); // 1.5 of 4 = 0.375, rounds half up

		assertThat(region(result, "lats_lower").coverage()).isEqualTo(0.17);
		assertThat(region(result, "chest_mid").coverage()).isEqualTo(0.67);
		assertThat(region(result, "biceps_long").coverage()).isEqualTo(0.38);
	}

	@Test
	void oneThirdRoundsTo033() {
		CoverageResult result = CoverageCalculator.calculate(targets("calves", 3),
			List.of(set("Calf raise", TUE, List.of("calves"), List.of())));

		assertThat(region(result, "calves").coverage()).isEqualTo(0.33);
	}

	@Test
	void aTargetOfZeroGivesCoverageZeroButStillShowsTheSets() {
		CoverageResult result = CoverageCalculator.calculate(targets("calves", 0),
			times(4, set("Calf raise", TUE, List.of("calves"), List.of())));

		RegionCoverage calves = region(result, "calves");
		assertThat(calves.effective()).isEqualTo(4.0);
		assertThat(calves.target()).isZero();
		assertThat(calves.coverage()).isZero();
		assertThat(calves.contributions()).hasSize(1);
	}

	// ---- week percent ----

	@Test
	void weekPercentIsTheAverageCoverageAsAWholeNumber() {
		// 1.0 and 0.5 -> 75%
		List<CountedSet> sets = new ArrayList<>();
		sets.addAll(times(6, set("Push-up", TUE, List.of("chest_mid"), List.of())));
		sets.addAll(times(3, set("Row", TUE, List.of("lats_lower"), List.of())));

		CoverageResult result = CoverageCalculator.calculate(targets("chest_mid", 6, "lats_lower", 6), sets);

		assertThat(result.weekPercent()).isEqualTo(75);
	}

	@Test
	void regionsWithTargetZeroAreLeftOutOfTheWeekPercent() {
		// Only chest_mid (fully covered) counts; the ignored calves would otherwise halve the average.
		CoverageResult result = CoverageCalculator.calculate(targets("chest_mid", 6, "calves", 0),
			times(6, set("Push-up", TUE, List.of("chest_mid"), List.of())));

		assertThat(result.weekPercent()).isEqualTo(100);
	}

	@Test
	void weekPercentIsRounded() {
		// 1 of 3 regions full = 33.3% -> 33; 2 of 3 = 66.7% -> 67
		Map<String, Integer> targets = targets("chest_mid", 1, "lats_lower", 1, "calves", 1);
		CountedSet chest = set("Push-up", TUE, List.of("chest_mid"), List.of());
		CountedSet lats = set("Row", TUE, List.of("lats_lower"), List.of());

		assertThat(CoverageCalculator.calculate(targets, List.of(chest)).weekPercent()).isEqualTo(33);
		assertThat(CoverageCalculator.calculate(targets, List.of(chest, lats)).weekPercent()).isEqualTo(67);
	}

	@Test
	void weekPercentUsesCappedCoverage() {
		// 20 sets of chest can't make up for untrained lats: (1.0 + 0) / 2 = 50%.
		CoverageResult result = CoverageCalculator.calculate(targets("chest_mid", 6, "lats_lower", 6),
			times(20, set("Push-up", TUE, List.of("chest_mid"), List.of())));

		assertThat(result.weekPercent()).isEqualTo(50);
	}

	@Test
	void allTargetsZeroGivesWeekPercentZero() {
		CoverageResult result = CoverageCalculator.calculate(targets("chest_mid", 0, "lats_lower", 0),
			times(3, set("Push-up", TUE, List.of("chest_mid"), List.of())));

		assertThat(result.weekPercent()).isZero();
	}

	@Test
	void allRegionsFullGivesOneHundredPercent() {
		List<CountedSet> sets = new ArrayList<>();
		sets.addAll(times(6, set("Push-up", TUE, List.of("chest_mid"), List.of())));
		sets.addAll(times(8, set("Row", TUE, List.of("lats_lower"), List.of("biceps_long"))));

		CoverageResult result = CoverageCalculator.calculate(targets("chest_mid", 6, "lats_lower", 6,
			"biceps_long", 4), sets);

		assertThat(result.weekPercent()).isEqualTo(100);
		assertThat(result.regions()).extracting(RegionCoverage::coverage).containsOnly(1.0);
	}

	// ---- empty week ----

	@Test
	void anEmptyWeekIsAllZeros() {
		CoverageResult result = CoverageCalculator.calculate(targets("chest_upper", 6, "chest_mid", 6, "calves", 6),
			List.of());

		assertThat(result.weekPercent()).isZero();
		assertThat(result.regions()).hasSize(3).allSatisfy(region -> {
			assertThat(region.effective()).isZero();
			assertThat(region.coverage()).isZero();
			assertThat(region.contributions()).isEmpty();
		});
	}

	@Test
	void regionsKeepTheOrderOfTheTargets() {
		CoverageResult result = CoverageCalculator.calculate(targets("chest_upper", 6, "lats_lower", 6,
			"biceps_long", 4, "calves", 6), List.of());

		assertThat(result.regions()).extracting(RegionCoverage::regionId)
			.containsExactly("chest_upper", "lats_lower", "biceps_long", "calves");
	}

	// ---- contributions ----

	@Test
	void setsOfTheSameExerciseOnTheSameDayAreOneContribution() {
		CoverageResult result = CoverageCalculator.calculate(targets("lats_lower", 6, "biceps_long", 4),
			times(3, set("Single-arm cable row", TUE, List.of("lats_lower"), List.of("biceps_long"))));

		assertThat(region(result, "lats_lower").contributions())
			.containsExactly(new Contribution("Single-arm cable row", TUE, 3.0));
		assertThat(region(result, "biceps_long").contributions())
			.containsExactly(new Contribution("Single-arm cable row", TUE, 1.5));
	}

	@Test
	void theSameExerciseOnTwoDaysIsTwoContributions() {
		List<CountedSet> sets = new ArrayList<>();
		sets.addAll(times(3, set("Push-up", TUE, List.of("chest_mid"), List.of())));
		sets.addAll(times(2, set("Push-up", THU, List.of("chest_mid"), List.of())));

		CoverageResult result = CoverageCalculator.calculate(targets("chest_mid", 6), sets);

		assertThat(region(result, "chest_mid").contributions()).containsExactly(
			new Contribution("Push-up", TUE, 3.0),
			new Contribution("Push-up", THU, 2.0));
	}

	@Test
	void contributionsAreSortedByDayThenExerciseName() {
		List<CountedSet> sets = List.of(
			set("Push-up", THU, List.of("chest_mid"), List.of()),
			set("Push-up", TUE, List.of("chest_mid"), List.of()),
			set("Cable fly", THU, List.of("chest_mid"), List.of()),
			set("Incline press", TUE, List.of("chest_mid"), List.of()),
			set("Dip", MON, List.of(), List.of("chest_mid")));

		CoverageResult result = CoverageCalculator.calculate(targets("chest_mid", 6), sets);

		assertThat(region(result, "chest_mid").contributions()).containsExactly(
			new Contribution("Dip", MON, 0.5),
			new Contribution("Incline press", TUE, 1.0),
			new Contribution("Push-up", TUE, 1.0),
			new Contribution("Cable fly", THU, 1.0),
			new Contribution("Push-up", THU, 1.0));
	}

	@Test
	void theOrderOfTheSetsDoesNotMatter() {
		List<CountedSet> sets = new ArrayList<>(List.of(
			set("Push-up", THU, List.of("chest_mid"), List.of("delt_front")),
			set("Incline press", TUE, List.of("chest_upper", "chest_mid"), List.of("delt_front")),
			set("Row", MON, List.of("lats_lower"), List.of("biceps_long")),
			set("Incline press", TUE, List.of("chest_upper", "chest_mid"), List.of("delt_front"))));
		Map<String, Integer> targets = targets("chest_upper", 6, "chest_mid", 6, "delt_front", 4, "lats_lower", 6,
			"biceps_long", 4);

		CoverageResult forwards = CoverageCalculator.calculate(targets, sets);
		Collections.reverse(sets);
		CoverageResult backwards = CoverageCalculator.calculate(targets, sets);

		assertThat(backwards).isEqualTo(forwards);
	}

	@Test
	void creditIsOneForPrimaryAndHalfForSecondary() {
		assertThat(CoverageCalculator.creditFor(RegionRole.PRIMARY)).isEqualTo(1.0);
		assertThat(CoverageCalculator.creditFor(RegionRole.SECONDARY)).isEqualTo(0.5);
	}

	// ---- helpers ----

	private static CountedSet set(String exercise, LocalDate day, List<String> primary, List<String> secondary) {
		return new CountedSet(exercise, day, primary, secondary);
	}

	private static List<CountedSet> times(int count, CountedSet set) {
		return Collections.nCopies(count, set);
	}

	/** targets("chest_mid", 6, "calves", 4) -> an ordered map. */
	private static Map<String, Integer> targets(Object... regionAndTarget) {
		Map<String, Integer> targets = new LinkedHashMap<>();
		for (int i = 0; i < regionAndTarget.length; i += 2) {
			targets.put((String) regionAndTarget[i], (Integer) regionAndTarget[i + 1]);
		}
		return targets;
	}

	private static RegionCoverage region(CoverageResult result, String regionId) {
		return result.regions().stream()
			.filter(region -> region.regionId().equals(regionId))
			.findFirst()
			.orElseThrow();
	}

}
