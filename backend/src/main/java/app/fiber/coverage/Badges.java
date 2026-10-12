package app.fiber.coverage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The six muscle-group badges: a group's badge is earned for the week when every region in it is DONE.
 *
 * Pure Java, no Spring, no database: region states in, badges out.
 */
public final class Badges {

	/** The badges always come in this order. */
	private static final List<String> GROUP_ORDER = List.of("chest", "shoulders", "back", "arms", "core", "legs");

	private Badges() {
	}

	/**
	 * One badge per group, in the order chest, shoulders, back, arms, core, legs.
	 *
	 * @param regions       the week's regions with their state
	 * @param groupByRegion region id to its muscle group, e.g. "lats_upper" to "back". Regions without
	 *                      a known group are left out.
	 */
	public static List<Badge> forWeek(List<RegionCoverage> regions, Map<String, String> groupByRegion) {
		List<Badge> badges = new ArrayList<>();
		for (String group : GROUP_ORDER) {
			List<RegionCoverage> inGroup = regions.stream()
				.filter(region -> group.equals(groupByRegion.get(region.regionId())))
				.toList();
			int done = (int) inGroup.stream().filter(region -> region.state() == RegionState.DONE).count();
			// A group without any region has nothing to finish, so it never counts as done.
			boolean allDone = !inGroup.isEmpty() && done == inGroup.size();
			badges.add(new Badge(group, displayName(group), allDone, done, inGroup.size()));
		}
		return badges;
	}

	/** The groups that are done in {@code after} but were not done in {@code before}, in group order. */
	public static List<String> earned(List<Badge> before, List<Badge> after) {
		List<String> wasDone = before.stream().filter(Badge::done).map(Badge::group).toList();
		return after.stream()
			.filter(Badge::done)
			.map(Badge::group)
			.filter(group -> !wasDone.contains(group))
			.toList();
	}

	/** "chest" -> "Chest". */
	static String displayName(String group) {
		return group.substring(0, 1).toUpperCase(Locale.ROOT) + group.substring(1);
	}

}
