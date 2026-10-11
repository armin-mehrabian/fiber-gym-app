package app.fiber.recommend;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Builds the next workout: picks the next split day, then greedily picks up to 5 exercises that
 * fill this week's biggest gaps on that day's regions.
 *
 * 1. Day: the split day after the day of the last finished workout (wrapping around after the last
 *    day). No history, or a last workout without a split day: the first day.
 * 2. Gaps: for each region of that day that isn't recovering, gap = max(0, target - effective sets this week).
 * 3. Candidates: exercises with at least one primary region that is on that day and not recovering.
 * 4. Greedy: score = sum of gap x weight (1 primary, 0.5 secondary) over that day's regions;
 *    x0.5 if its movement pattern was already picked twice; x0.7 if its main region is already
 *    the main region of a picked exercise. Take the best score (a tie goes to the lowest id),
 *    assume 3 sets, so each of its regions' gap goes down by 3 x weight. Repeat until 5 exercises
 *    are picked or the best score is 0.
 *
 * Pure Java, no Spring, no database.
 */
public final class Recommender {

	public static final int MAX_EXERCISES = 5;
	/** Every recommended exercise is planned with this many working sets. */
	public static final int SETS_PER_EXERCISE = 3;

	static final double SAME_PATTERN_FACTOR = 0.5;
	static final int PATTERN_LIMIT = 2;
	static final double SAME_MAIN_REGION_FACTOR = 0.7;

	private Recommender() {
	}

	/**
	 * @param splitDays      the user's split days (at least one)
	 * @param lastSplitDayId the split day of the last finished workout, or null
	 * @param targets        weekly target per region id
	 * @param effective      effective sets this week per region id (missing = 0)
	 * @param recovering     regions that are still recovering
	 * @param exercises      the exercises the user can do (their equipment, not retired)
	 */
	public static Recommendation recommend(List<SplitDayOption> splitDays, UUID lastSplitDayId,
			Map<String, Integer> targets, Map<String, Double> effective, Set<String> recovering,
			List<Candidate> exercises) {
		SplitDayOption day = nextDay(splitDays, lastSplitDayId);
		Map<String, Double> gaps = gaps(day.regionIds(), targets, effective, recovering);
		List<Candidate> picked = pick(gaps, candidates(day.regionIds(), recovering, exercises));
		return new Recommendation(day, picked.stream().map(Candidate::id).toList(), planned(picked));
	}

	/** The day after the last workout's day, in rotation order; the first day if that is unknown. */
	static SplitDayOption nextDay(List<SplitDayOption> splitDays, UUID lastSplitDayId) {
		if (splitDays.isEmpty()) {
			throw new IllegalArgumentException("The user has no split days");
		}
		List<SplitDayOption> inOrder = splitDays.stream()
			.sorted(Comparator.comparingInt(SplitDayOption::position))
			.toList();
		for (int i = 0; i < inOrder.size(); i++) {
			if (inOrder.get(i).id().equals(lastSplitDayId)) {
				return inOrder.get((i + 1) % inOrder.size());
			}
		}
		return inOrder.getFirst();
	}

	/** gap = max(0, target - effective) for each allowed region that isn't recovering. */
	static Map<String, Double> gaps(Set<String> allowed, Map<String, Integer> targets,
			Map<String, Double> effective, Set<String> recovering) {
		Map<String, Double> gaps = new HashMap<>();
		for (String region : allowed) {
			if (!recovering.contains(region)) {
				double gap = targets.getOrDefault(region, 0) - effective.getOrDefault(region, 0.0);
				gaps.put(region, Math.max(0, gap));
			}
		}
		return gaps;
	}

	/**
	 * Exercises with at least one primary region that is allowed today and not recovering.
	 * Sorted by id, so the first of equally good exercises is always the one with the lowest id.
	 */
	public static List<Candidate> candidates(Set<String> allowed, Set<String> recovering, List<Candidate> exercises) {
		return exercises.stream()
			.filter(exercise -> exercise.primary().stream()
				.anyMatch(region -> allowed.contains(region) && !recovering.contains(region)))
			.sorted(Comparator.comparing(Candidate::id))
			.toList();
	}

	/** The greedy loop: best score first, then the gaps shrink, until 5 picks or nothing left to fill. */
	static List<Candidate> pick(Map<String, Double> startGaps, List<Candidate> candidates) {
		Map<String, Double> gaps = new HashMap<>(startGaps);
		List<Candidate> remaining = new ArrayList<>(candidates);
		List<Candidate> picked = new ArrayList<>();
		while (picked.size() < MAX_EXERCISES) {
			Candidate best = null;
			double bestScore = 0;
			for (Candidate candidate : remaining) {
				double score = score(candidate, gaps, picked);
				// Strictly greater: on a tie the earlier candidate (lower id) stays.
				if (score > bestScore) {
					best = candidate;
					bestScore = score;
				}
			}
			if (best == null) {
				break; // the best score is 0: every gap it could fill is already full
			}
			picked.add(best);
			remaining.remove(best);
			fill(gaps, best);
		}
		return picked;
	}

	/** How much this exercise would fill the open gaps, with the penalties for repeating itself. */
	static double score(Candidate candidate, Map<String, Double> gaps, List<Candidate> picked) {
		double score = 0;
		for (Map.Entry<String, Double> region : candidate.regionWeights().entrySet()) {
			// Regions not in gaps (other days' regions, recovering ones) add nothing.
			score += gaps.getOrDefault(region.getKey(), 0.0) * region.getValue();
		}
		long samePattern = picked.stream().filter(p -> p.pattern().equals(candidate.pattern())).count();
		if (samePattern >= PATTERN_LIMIT) {
			score *= SAME_PATTERN_FACTOR;
		}
		boolean sameMainRegion = picked.stream()
			.anyMatch(p -> p.mainRegion() != null && p.mainRegion().equals(candidate.mainRegion()));
		if (sameMainRegion) {
			score *= SAME_MAIN_REGION_FACTOR;
		}
		return roundScore(score);
	}

	/**
	 * Rounded to 6 decimals, so tiny floating-point noise (x0.7 isn't exact in binary) can never
	 * decide between two exercises that are really equally good: the lower id should win.
	 */
	static double roundScore(double score) {
		return Math.round(score * 1_000_000) / 1_000_000.0;
	}

	/** After picking an exercise: its 3 planned sets fill each region's gap by 3 x weight (never below 0). */
	private static void fill(Map<String, Double> gaps, Candidate picked) {
		for (Map.Entry<String, Double> region : picked.regionWeights().entrySet()) {
			gaps.computeIfPresent(region.getKey(),
				(id, gap) -> Math.max(0, gap - SETS_PER_EXERCISE * region.getValue()));
		}
	}

	/** Region id to 1 if it is primary for any picked exercise, else 0.5 if it is secondary for any. */
	public static Map<String, Double> planned(List<Candidate> picked) {
		Map<String, Double> planned = new TreeMap<>();
		for (Candidate exercise : picked) {
			exercise.regionWeights().forEach((region, weight) -> planned.merge(region, weight, Math::max));
		}
		return planned;
	}

}
