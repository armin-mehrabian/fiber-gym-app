package app.fiber.coverage;

import java.time.LocalDate;
import java.util.List;

/**
 * One set that counts for weekly coverage: a completed working set (not a warm-up) of a finished
 * workout. {@code day} is the day the workout started, in the user's timezone.
 * The region lists say which regions the exercise emphasises mainly (primary) and which it helps (secondary).
 */
public record CountedSet(
	String exerciseName,
	LocalDate day,
	List<String> primaryRegionIds,
	List<String> secondaryRegionIds) {
}
