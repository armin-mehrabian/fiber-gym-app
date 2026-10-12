package app.fiber.coverage;

import java.util.List;

/** One week's coverage (week percent and every region with its state) plus the six muscle-group badges. */
public record WeekCoverage(CoverageResult coverage, List<Badge> badges) {
}
