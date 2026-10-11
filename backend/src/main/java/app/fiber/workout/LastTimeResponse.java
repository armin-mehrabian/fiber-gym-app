package app.fiber.workout;

import java.time.LocalDate;
import java.util.List;

/**
 * The last finished workout with an exercise: the day it started (user's timezone, "YYYY-MM-DD")
 * and its completed working sets.
 */
public record LastTimeResponse(LocalDate date, List<PastSetResponse> sets) {
}
