package app.fiber.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SplitPresetsTest {

	private static final List<String> PUSH = List.of(
		"chest_upper", "chest_mid", "chest_lower", "delt_front", "delt_side", "triceps_long", "triceps_lateral");
	private static final List<String> PULL = List.of(
		"traps_upper", "traps_mid", "lats_upper", "lats_lower", "delt_rear", "biceps_long", "biceps_short",
		"forearms");
	private static final List<String> LEGS = List.of(
		"quads_rectus", "quads_vasti", "adductors", "glutes", "glute_med", "hamstrings", "calves", "lower_back",
		"abs_upper", "abs_lower", "obliques");

	@Test
	void pplIsPushPullLegsInThatOrder() {
		List<SplitDayPlan> days = SplitPresets.daysFor(SplitType.PPL);

		assertThat(days).extracting(SplitDayPlan::name).containsExactly("Push", "Pull", "Legs");
		assertThat(days.get(0).regions()).containsExactlyInAnyOrderElementsOf(PUSH);
		assertThat(days.get(1).regions()).containsExactlyInAnyOrderElementsOf(PULL);
		assertThat(days.get(2).regions()).containsExactlyInAnyOrderElementsOf(LEGS);
	}

	@Test
	void upperIsPushPlusPullAndLowerIsLegs() {
		List<SplitDayPlan> days = SplitPresets.daysFor(SplitType.UL);

		assertThat(days).extracting(SplitDayPlan::name).containsExactly("Upper", "Lower");
		assertThat(days.get(0).regions()).hasSize(15).containsAll(PUSH).containsAll(PULL);
		assertThat(days.get(1).regions()).containsExactlyInAnyOrderElementsOf(LEGS);
	}

	@Test
	void fullBodyIsOneDayWithAll26Regions() {
		List<SplitDayPlan> days = SplitPresets.daysFor(SplitType.FB);

		assertThat(days).hasSize(1);
		assertThat(days.get(0).name()).isEqualTo("Full body");
		assertThat(days.get(0).regions()).hasSize(26).doesNotHaveDuplicates()
			.containsAll(PUSH).containsAll(PULL).containsAll(LEGS);
	}

	// Push, Pull and Legs must not overlap, otherwise Upper and Full body would list a region twice.
	@Test
	void pushPullAndLegsShareNoRegion() {
		Set<String> all = new HashSet<>();
		all.addAll(PUSH);
		all.addAll(PULL);
		all.addAll(LEGS);

		assertThat(all).hasSize(PUSH.size() + PULL.size() + LEGS.size()).hasSize(26);
	}

	@Test
	void customHasNoPreset() {
		assertThatThrownBy(() -> SplitPresets.daysFor(SplitType.CUSTOM))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
