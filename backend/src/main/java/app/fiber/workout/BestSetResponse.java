package app.fiber.workout;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The user's best working set of an exercise (highest estimated one-rep max) and the day it was done. */
public record BestSetResponse(BigDecimal weightKg, int reps, LocalDate date) {
}
