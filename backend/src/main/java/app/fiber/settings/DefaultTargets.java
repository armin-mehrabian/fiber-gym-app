package app.fiber.settings;

import java.util.Set;

/**
 * The weekly set target every new user starts with, per region. Users can change them later.
 *
 * - 6 sets for the main regions listed below.
 * - 4 sets for every other region (front delts, upper traps, lower back, biceps, triceps, forearms,
 *   abs, obliques, rectus femoris, adductors, glute med). These get a lot of half-set credit from
 *   compound lifts (e.g. presses also work the front delts and triceps, rows the biceps and forearms),
 *   so a lower target lets a normal 3-5 day week fill the body figure.
 */
public final class DefaultTargets {

	public static final int MAIN_REGION_SETS = 6;
	public static final int OTHER_REGION_SETS = 4;

	static final Set<String> MAIN_REGIONS = Set.of(
		"chest_upper", "chest_mid", "chest_lower", "delt_side", "delt_rear", "traps_mid",
		"lats_upper", "lats_lower", "quads_vasti", "glutes", "hamstrings", "calves");

	private DefaultTargets() {
	}

	public static int weeklySetsFor(String regionId) {
		return MAIN_REGIONS.contains(regionId) ? MAIN_REGION_SETS : OTHER_REGION_SETS;
	}

}
