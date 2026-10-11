package app.fiber.progression;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Personal bests. The best set is the one with the highest estimated one-rep max (Epley formula):
 * weight x (1 + reps / 30). So 100 kg x 5 (about 116.7) beats 110 kg x 1 (about 113.7).
 * Bodyweight sets (weight 0) all have an estimate of 0, so between two of them the reps decide.
 *
 * Pure Java, no Spring, no database.
 */
public final class PersonalBests {

	private PersonalBests() {
	}

	/** weight x (1 + reps / 30). Only for tests and explaining the formula; comparing uses the exact form below. */
	static double estimatedOneRepMax(LoggedSet set) {
		return set.weightKg().doubleValue() * (1 + set.reps() / 30.0);
	}

	/**
	 * Positive if {@code a} is a better set than {@code b}, 0 if equally good, negative if worse.
	 * Compares weight x (30 + reps), which is the estimated one-rep max times 30: the same order,
	 * but exact (no rounding), so two equally good sets really count as equal.
	 */
	public static int compare(LoggedSet a, LoggedSet b) {
		int byEstimate = scaledEstimate(a).compareTo(scaledEstimate(b));
		if (byEstimate != 0) {
			return byEstimate;
		}
		if (a.isBodyweight() && b.isBodyweight()) {
			return Integer.compare(a.reps(), b.reps());
		}
		return 0;
	}

	/** The best of these items' sets; on a tie, the one that comes first in the list. Empty list = empty. */
	public static <T> Optional<T> best(List<T> items, Function<T, LoggedSet> setOf) {
		T best = null;
		for (T item : items) {
			if (best == null || compare(setOf.apply(item), setOf.apply(best)) > 0) {
				best = item;
			}
		}
		return Optional.ofNullable(best);
	}

	/**
	 * True if this workout's best set beats the best set of every earlier workout.
	 * A first attempt (no earlier sets) is not a personal best: there is nothing to beat yet.
	 */
	public static boolean isPersonalBest(List<LoggedSet> thisWorkout, List<LoggedSet> earlierWorkouts) {
		Optional<LoggedSet> now = best(thisWorkout, Function.identity());
		Optional<LoggedSet> before = best(earlierWorkouts, Function.identity());
		return now.isPresent() && before.isPresent() && compare(now.get(), before.get()) > 0;
	}

	private static BigDecimal scaledEstimate(LoggedSet set) {
		return set.weightKg().multiply(BigDecimal.valueOf(30L + set.reps()));
	}

}
