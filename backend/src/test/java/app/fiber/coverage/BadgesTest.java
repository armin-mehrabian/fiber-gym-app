package app.fiber.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The six muscle-group badges, without Spring or a database. */
class BadgesTest {

	/** A small body: 2 chest regions, 1 shoulder region, 1 legs region. Other groups have none. */
	private static final Map<String, String> GROUPS = Map.of(
		"chest_upper", "chest",
		"chest_mid", "chest",
		"delt_side", "shoulders",
		"calves", "legs");

	@Test
	void thereAreSixBadgesInAFixedOrderWithCapitalisedNames() {
		List<Badge> badges = Badges.forWeek(List.of(), GROUPS);

		assertThat(badges).extracting(Badge::group)
			.containsExactly("chest", "shoulders", "back", "arms", "core", "legs");
		assertThat(badges).extracting(Badge::name)
			.containsExactly("Chest", "Shoulders", "Back", "Arms", "Core", "Legs");
	}

	@Test
	void aGroupIsDoneWhenEveryRegionInItIsDone() {
		List<Badge> badges = Badges.forWeek(regions(
			"chest_upper", RegionState.DONE, "chest_mid", RegionState.DONE), GROUPS);

		assertThat(badge(badges, "chest")).isEqualTo(new Badge("chest", "Chest", true, 2, 2));
	}

	@Test
	void oneRegionNotDoneKeepsTheGroupOpen() {
		List<Badge> started = Badges.forWeek(regions(
			"chest_upper", RegionState.DONE, "chest_mid", RegionState.STARTED), GROUPS);
		List<Badge> notYet = Badges.forWeek(regions(
			"chest_upper", RegionState.NOT_YET, "chest_mid", RegionState.NOT_YET), GROUPS);

		assertThat(badge(started, "chest")).isEqualTo(new Badge("chest", "Chest", false, 1, 2));
		assertThat(badge(notYet, "chest")).isEqualTo(new Badge("chest", "Chest", false, 0, 2));
	}

	@Test
	void eachGroupCountsOnlyItsOwnRegions() {
		List<Badge> badges = Badges.forWeek(regions(
			"chest_upper", RegionState.DONE,
			"delt_side", RegionState.STARTED,
			"chest_mid", RegionState.DONE,
			"calves", RegionState.DONE), GROUPS);

		assertThat(badges).extracting(Badge::done).containsExactly(true, false, false, false, false, true);
		assertThat(badges).extracting(Badge::regionsDone).containsExactly(2, 0, 0, 0, 0, 1);
		assertThat(badges).extracting(Badge::regionsTotal).containsExactly(2, 1, 0, 0, 0, 1);
	}

	@Test
	void aGroupWithoutRegionsIsNeverDone() {
		Badge back = badge(Badges.forWeek(regions("chest_mid", RegionState.DONE), GROUPS), "back");

		assertThat(back).isEqualTo(new Badge("back", "Back", false, 0, 0));
	}

	@Test
	void regionsOfAnUnknownGroupAreIgnored() {
		List<Badge> badges = Badges.forWeek(regions(
			"neck", RegionState.DONE, "chest_upper", RegionState.DONE), Map.of("neck", "head",
			"chest_upper", "chest"));

		assertThat(badges).hasSize(6);
		assertThat(badges).extracting(Badge::regionsTotal).containsExactly(1, 0, 0, 0, 0, 0);
	}

	@Test
	void aRegionWithTarget0NeverBlocksItsGroup() {
		// chest_upper has target 0, so it is DONE (nothing asked for); chest_mid gets 2 of 2.
		CountedSet pushUp = new CountedSet("Push-up", LocalDate.of(2026, 10, 6), List.of("chest_mid"), List.of());
		CoverageResult week = CoverageCalculator.calculate(targets("chest_upper", 0, "chest_mid", 2),
			List.of(pushUp, pushUp));

		assertThat(badge(Badges.forWeek(week.regions(), GROUPS), "chest").done()).isTrue();
	}

	// ---- badges earned by one workout ----

	@Test
	void aGroupDoneAfterButNotBeforeIsEarned() {
		List<Badge> before = List.of(new Badge("chest", "Chest", false, 1, 2), new Badge("legs", "Legs", false, 0, 1));
		List<Badge> after = List.of(new Badge("chest", "Chest", true, 2, 2), new Badge("legs", "Legs", true, 1, 1));

		assertThat(Badges.earned(before, after)).containsExactly("chest", "legs");
	}

	@Test
	void aGroupAlreadyDoneBeforeIsNotEarnedAgain() {
		List<Badge> before = List.of(new Badge("chest", "Chest", true, 2, 2), new Badge("legs", "Legs", false, 0, 1));
		List<Badge> after = List.of(new Badge("chest", "Chest", true, 2, 2), new Badge("legs", "Legs", false, 0, 1));

		assertThat(Badges.earned(before, after)).isEmpty();
	}

	@Test
	void earnedBadgesComeInBadgeOrder() {
		List<Badge> before = Badges.forWeek(regions(
			"chest_upper", RegionState.STARTED, "chest_mid", RegionState.DONE, "calves", RegionState.NOT_YET), GROUPS);
		List<Badge> after = Badges.forWeek(regions(
			"calves", RegionState.DONE, "chest_upper", RegionState.DONE, "chest_mid", RegionState.DONE), GROUPS);

		assertThat(Badges.earned(before, after)).containsExactly("chest", "legs");
	}

	// ---- helpers ----

	/** regions("chest_mid", DONE, "calves", NOT_YET) -> those regions with those states, in that order. */
	private static List<RegionCoverage> regions(Object... regionAndState) {
		List<RegionCoverage> regions = new ArrayList<>();
		for (int i = 0; i < regionAndState.length; i += 2) {
			regions.add(new RegionCoverage((String) regionAndState[i], 0, 6, 0, (RegionState) regionAndState[i + 1],
				List.of()));
		}
		return regions;
	}

	private static Map<String, Integer> targets(Object... regionAndTarget) {
		Map<String, Integer> targets = new LinkedHashMap<>();
		for (int i = 0; i < regionAndTarget.length; i += 2) {
			targets.put((String) regionAndTarget[i], (Integer) regionAndTarget[i + 1]);
		}
		return targets;
	}

	private static Badge badge(List<Badge> badges, String group) {
		return badges.stream().filter(badge -> badge.group().equals(group)).findFirst().orElseThrow();
	}

}
