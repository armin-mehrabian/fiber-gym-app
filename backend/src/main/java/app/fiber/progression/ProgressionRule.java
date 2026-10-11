package app.fiber.progression;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Double progression: suggests the weight and rep target for an exercise from the completed
 * working sets of the last finished workout that had it.
 *
 * - No history: no weight, aim for the top of the rep range.
 * - Bodyweight last time (weight 0): weight 0, one more rep than the best set (at most 100).
 * - Every set reached the top of the range: add weight (+5 kg for lower-body patterns,
 *   +2.5 kg for everything else) and start again at the bottom of the range.
 * - Otherwise: the same weight, aiming for one more rep than the weakest set (at most the top).
 *
 * Pure Java, no Spring, no database.
 */
public final class ProgressionRule {

	/** Leg exercises move more weight, so they go up in bigger steps. */
	static final Set<String> LOWER_BODY_PATTERNS = Set.of("squat", "hinge", "lunge", "calf", "knee_extension",
		"knee_flexion", "hip_abduction", "hip_adduction");

	static final BigDecimal LOWER_BODY_INCREMENT = new BigDecimal("5.00");
	static final BigDecimal UPPER_BODY_INCREMENT = new BigDecimal("2.50");

	/** The most reps a set can have, so a bodyweight target never goes past what can be logged. */
	static final int MAX_REPS = 100;

	private ProgressionRule() {
	}

	/**
	 * @param pattern       the exercise's movement pattern, e.g. "squat"
	 * @param repLow        bottom of the exercise's rep range
	 * @param repHigh       top of the exercise's rep range
	 * @param lastWorkingSets the completed working sets of the last finished workout with this
	 *                      exercise; empty if there is none
	 */
	public static Suggestion suggest(String pattern, int repLow, int repHigh, List<LoggedSet> lastWorkingSets) {
		if (lastWorkingSets.isEmpty()) {
			return new Suggestion(null, repHigh, SuggestionReason.NO_HISTORY);
		}
		BigDecimal topWeight = topWeight(lastWorkingSets);
		if (topWeight.signum() == 0) {
			int bestReps = lastWorkingSets.stream().mapToInt(LoggedSet::reps).max().orElseThrow();
			return new Suggestion(kg(BigDecimal.ZERO), Math.min(MAX_REPS, bestReps + 1), SuggestionReason.MORE_REPS);
		}
		boolean allHitTheTop = lastWorkingSets.stream().allMatch(set -> set.reps() >= repHigh);
		if (allHitTheTop) {
			return new Suggestion(kg(topWeight.add(increment(pattern))), repLow, SuggestionReason.INCREASE);
		}
		int lowestReps = lastWorkingSets.stream().mapToInt(LoggedSet::reps).min().orElseThrow();
		return new Suggestion(kg(topWeight), Math.min(repHigh, lowestReps + 1), SuggestionReason.REPEAT);
	}

	/** +5 kg for lower-body patterns, +2.5 kg for everything else. */
	static BigDecimal increment(String pattern) {
		return LOWER_BODY_PATTERNS.contains(pattern) ? LOWER_BODY_INCREMENT : UPPER_BODY_INCREMENT;
	}

	/** The heaviest working set last time: that is the weight to repeat or build on. */
	private static BigDecimal topWeight(List<LoggedSet> sets) {
		return sets.stream().map(LoggedSet::weightKg).max(Comparator.naturalOrder()).orElseThrow();
	}

	/** Weights are always sent with 2 decimals, e.g. 22.50. */
	private static BigDecimal kg(BigDecimal weight) {
		return weight.setScale(2, RoundingMode.HALF_UP);
	}

}
