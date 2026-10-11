package app.fiber.coverage;

import app.fiber.catalog.RegionRole;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Weekly coverage: how much of each region's weekly target the counted sets of one week fill.
 *
 * Rules:
 * - A set gives 1 effective set to each region the exercise hits mainly (primary)
 *   and 0.5 to each region it helps (secondary).
 * - coverage = effective / target, capped at 1. A target of 0 means "ignore this region": coverage 0.
 * - weekPercent = the average coverage of all regions with a target above 0, as a whole percentage.
 *
 * Pure Java, no Spring, no database: sets in, coverage out.
 */
public final class CoverageCalculator {

	/** A set counts fully for the regions an exercise hits mainly. */
	public static final double PRIMARY_CREDIT = 1.0;
	/** Helping regions count half. */
	public static final double SECONDARY_CREDIT = 0.5;

	private CoverageCalculator() {
	}

	/** How many effective sets one set gives a region with this role. */
	public static double creditFor(RegionRole role) {
		return role == RegionRole.PRIMARY ? PRIMARY_CREDIT : SECONDARY_CREDIT;
	}

	/**
	 * @param targets weekly target per region id. The result lists the regions in this map's order,
	 *                so pass them in region order (e.g. a LinkedHashMap).
	 * @param sets    the week's counted sets. Regions without a target are ignored.
	 */
	public static CoverageResult calculate(Map<String, Integer> targets, List<CountedSet> sets) {
		Map<String, List<Contribution>> contributionsByRegion = contributionsByRegion(sets);
		List<RegionCoverage> regions = new ArrayList<>();
		for (Map.Entry<String, Integer> target : targets.entrySet()) {
			List<Contribution> contributions = contributionsByRegion.getOrDefault(target.getKey(), List.of());
			regions.add(regionCoverage(target.getKey(), target.getValue(), contributions));
		}
		return new CoverageResult(weekPercent(regions), List.copyOf(regions));
	}

	/**
	 * Per region: one contribution per (exercise, day), with the sets of that exercise on that day
	 * added up. Sorted by day, then by exercise name.
	 */
	static Map<String, List<Contribution>> contributionsByRegion(List<CountedSet> sets) {
		// region id -> (day + exercise) -> effective sets
		Map<String, Map<ContributionKey, Double>> totals = new HashMap<>();
		for (CountedSet set : sets) {
			ContributionKey key = new ContributionKey(set.day(), set.exerciseName());
			for (String regionId : set.primaryRegionIds()) {
				addCredit(totals, regionId, key, PRIMARY_CREDIT);
			}
			for (String regionId : set.secondaryRegionIds()) {
				addCredit(totals, regionId, key, SECONDARY_CREDIT);
			}
		}
		Map<String, List<Contribution>> result = new HashMap<>();
		totals.forEach((regionId, byKey) -> result.put(regionId, sortedContributions(byKey)));
		return result;
	}

	private static void addCredit(Map<String, Map<ContributionKey, Double>> totals, String regionId,
			ContributionKey key, double credit) {
		totals.computeIfAbsent(regionId, id -> new LinkedHashMap<>()).merge(key, credit, Double::sum);
	}

	private static List<Contribution> sortedContributions(Map<ContributionKey, Double> byKey) {
		return byKey.entrySet().stream()
			.map(entry -> new Contribution(entry.getKey().exerciseName(), entry.getKey().day(), entry.getValue()))
			.sorted(Comparator.comparing(Contribution::day).thenComparing(Contribution::exerciseName))
			.toList();
	}

	private static RegionCoverage regionCoverage(String regionId, int target, List<Contribution> contributions) {
		double effective = contributions.stream().mapToDouble(Contribution::sets).sum();
		return new RegionCoverage(regionId, round(effective, 1), target, round(coverage(effective, target), 2),
			contributions);
	}

	/** effective / target, never above 1 (extra sets don't fill more than the whole region). Target 0 gives 0. */
	static double coverage(double effective, int target) {
		if (target <= 0) {
			return 0;
		}
		return Math.min(1.0, effective / target);
	}

	/**
	 * The average coverage of the regions with a target above 0, as a whole percentage.
	 * Coverage is recomputed from each region's effective sets instead of using the 2-decimal coverage,
	 * so rounding happens only once, at the end. The 1-decimal effective value is exact here, because
	 * effective sets are always a multiple of 0.5.
	 * No region with a target gives 0.
	 */
	static int weekPercent(List<RegionCoverage> regions) {
		List<RegionCoverage> counted = regions.stream().filter(region -> region.target() > 0).toList();
		if (counted.isEmpty()) {
			return 0;
		}
		double average = counted.stream()
			.mapToDouble(region -> coverage(region.effective(), region.target()))
			.average()
			.orElse(0);
		return (int) Math.round(average * 100);
	}

	/** Rounds half up to the given number of decimals, e.g. round(1/3.0, 2) = 0.33. */
	static double round(double value, int decimals) {
		double factor = Math.pow(10, decimals);
		return Math.round(value * factor) / factor;
	}

	/** Contributions are grouped by this pair. */
	private record ContributionKey(LocalDate day, String exerciseName) {
	}

}
