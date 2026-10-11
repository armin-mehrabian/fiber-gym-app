package app.fiber.progression;

import java.math.BigDecimal;

/** One completed set as it was logged: weight in kg (0 = bodyweight) and reps. */
public record LoggedSet(BigDecimal weightKg, int reps) {

	/** Bodyweight sets are logged with a weight of 0. */
	public boolean isBodyweight() {
		return weightKg.signum() == 0;
	}

}
