package app.fiber.settings;

import java.util.ArrayList;
import java.util.List;

/**
 * The ready-made splits, in one place. Each day lists the regions the recommender may target
 * on that day. The days are in rotation order.
 *
 * - PPL: Push / Pull / Legs.
 * - UL:  Upper (= Push + Pull regions) / Lower (= Legs regions).
 * - FB:  one "Full body" day with all 26 regions.
 *
 * Core and lower back sit on the Legs day: squats and hinges already load them, and it keeps
 * the upper-body days focused.
 */
public final class SplitPresets {

	static final List<String> PUSH = List.of(
		"chest_upper", "chest_mid", "chest_lower", "delt_front", "delt_side", "triceps_long", "triceps_lateral");

	static final List<String> PULL = List.of(
		"traps_upper", "traps_mid", "lats_upper", "lats_lower", "delt_rear", "biceps_long", "biceps_short",
		"forearms");

	static final List<String> LEGS = List.of(
		"quads_rectus", "quads_vasti", "adductors", "glutes", "glute_med", "hamstrings", "calves", "lower_back",
		"abs_upper", "abs_lower", "obliques");

	private SplitPresets() {
	}

	/** The days of a preset split. CUSTOM has no preset: its days come from the user. */
	public static List<SplitDayPlan> daysFor(SplitType splitType) {
		return switch (splitType) {
			case PPL -> List.of(
				new SplitDayPlan("Push", PUSH),
				new SplitDayPlan("Pull", PULL),
				new SplitDayPlan("Legs", LEGS));
			case UL -> List.of(
				new SplitDayPlan("Upper", join(PUSH, PULL)),
				new SplitDayPlan("Lower", LEGS));
			case FB -> List.of(
				new SplitDayPlan("Full body", join(PUSH, PULL, LEGS)));
			case CUSTOM -> throw new IllegalArgumentException("A CUSTOM split has no preset; its days come from the user");
		};
	}

	@SafeVarargs
	private static List<String> join(List<String>... lists) {
		List<String> all = new ArrayList<>();
		for (List<String> list : lists) {
			all.addAll(list);
		}
		return all;
	}

}
