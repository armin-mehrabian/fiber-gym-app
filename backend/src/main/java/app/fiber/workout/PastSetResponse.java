package app.fiber.workout;

import java.math.BigDecimal;

/** A working set done before, e.g. {"weightKg": 20.00, "reps": 12}. */
public record PastSetResponse(BigDecimal weightKg, int reps) {
}
