package app.fiber.coverage;

import java.time.LocalDate;

/** What one exercise on one day gave a region, in effective sets (e.g. 3 or 1.5). */
public record Contribution(String exerciseName, LocalDate day, double sets) {
}
