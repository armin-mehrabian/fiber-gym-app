package app.fiber.coverage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Which regions are still recovering: a region is recovering if a single finished workout in the
 * last 48 hours gave it at least 3 effective sets.
 *
 * It is "a single workout", not "the last 48 hours added up": two light workouts of 2 sets each
 * don't make a region recovering, one hard workout of 3 sets does.
 *
 * Pure Java, no Spring, no database.
 */
public final class RecoveryCalculator {

	/** How long a region counts as recovering after a hard workout. */
	public static final Duration RECOVERY_WINDOW = Duration.ofHours(48);
	/** Effective sets in one workout that make a region need recovery. */
	public static final double HARD_SESSION_SETS = 3.0;

	private RecoveryCalculator() {
	}

	/** The earliest finish time that still counts: exactly 48 hours ago is still inside the window. */
	public static Instant windowStart(Instant now) {
		return now.minus(RECOVERY_WINDOW);
	}

	/** The ids of the recovering regions, sorted. Workouts that finished before the window are ignored. */
	public static Set<String> recoveringRegions(List<RecentSession> sessions, Instant now) {
		Instant windowStart = windowStart(now);
		Set<String> recovering = new TreeSet<>();
		for (RecentSession session : sessions) {
			if (session.finishedAt().isBefore(windowStart)) {
				continue;
			}
			for (Map.Entry<String, Double> region : session.effectiveSetsByRegion().entrySet()) {
				if (region.getValue() >= HARD_SESSION_SETS) {
					recovering.add(region.getKey());
				}
			}
		}
		return recovering;
	}

}
