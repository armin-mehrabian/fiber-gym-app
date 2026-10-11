package app.fiber.coverage;

import java.time.LocalDate;

/** One day of the week and how many finished workouts started on it (in the user's timezone). */
public record CoverageDayResponse(LocalDate date, int sessions) {
}
