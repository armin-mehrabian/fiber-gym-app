package app.fiber.coverage;

import java.time.Instant;
import java.util.Map;

/** A finished workout and the effective sets it gave each region (region id to sets, e.g. 3.0 or 1.5). */
public record RecentSession(Instant finishedAt, Map<String, Double> effectiveSetsByRegion) {
}
