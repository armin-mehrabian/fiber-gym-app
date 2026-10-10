package app.fiber.workout;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

/**
 * Body of PATCH /api/sets/{setId}. Every field is optional: null (or left out) = keep the current value.
 * Same limits as a new set: weight 0-1000 kg with at most 2 decimals, reps 0-100.
 */
public record SetUpdateRequest(
	@DecimalMin(value = "0", message = "must be between 0 and 1000")
	@DecimalMax(value = "1000", message = "must be between 0 and 1000")
	@Digits(integer = 4, fraction = 2, message = "must have at most 2 decimals")
	BigDecimal weightKg,

	@Min(value = 0, message = "must be between 0 and 100")
	@Max(value = 100, message = "must be between 0 and 100")
	Integer reps,

	Boolean isWarmup,

	Boolean completed) {
}
