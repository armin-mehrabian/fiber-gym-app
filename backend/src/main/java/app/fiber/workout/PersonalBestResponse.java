package app.fiber.workout;

import java.math.BigDecimal;

/** A new personal best in a workout summary: the exercise and its best set of that workout. */
public record PersonalBestResponse(String exerciseId, String name, BigDecimal weightKg, int reps) {
}
