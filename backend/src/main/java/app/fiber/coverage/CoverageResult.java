package app.fiber.coverage;

import java.util.List;

/** The whole week: one entry per region (in region order) and the week's overall percentage (0-100). */
public record CoverageResult(int weekPercent, List<RegionCoverage> regions) {
}
