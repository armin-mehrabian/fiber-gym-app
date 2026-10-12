package app.fiber.workout;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Body of POST /api/sessions/{id}/exercises/{position}/complete: log a whole exercise with one tap.
 * Same limits as a single set (weight 0-1000 kg with at most 2 decimals, 0 = bodyweight; reps 0-100),
 * plus how many sets to log: 1-10.
 */
public record CompleteExerciseRequest(
	@NotNull(message = "is required")
	@DecimalMin(value = "0", message = "must be between 0 and 1000")
	@DecimalMax(value = "1000", message = "must be between 0 and 1000")
	@Digits(integer = 4, fraction = 2, message = "must have at most 2 decimals")
	BigDecimal weightKg,

	@NotNull(message = "is required")
	@Min(value = 0, message = "must be between 0 and 100")
	@Max(value = 100, message = "must be between 0 and 100")
	Integer reps,

	@NotNull(message = "is required")
	@Min(value = 1, message = "must be between 1 and 10")
	@Max(value = 10, message = "must be between 1 and 10")
	Integer sets) {
}
