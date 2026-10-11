package app.fiber.workout;

import java.math.BigDecimal;

/** One completed set in an exercise's history, warm-ups included (marked). */
public record HistorySetResponse(BigDecimal weightKg, int reps, boolean isWarmup) {
}
