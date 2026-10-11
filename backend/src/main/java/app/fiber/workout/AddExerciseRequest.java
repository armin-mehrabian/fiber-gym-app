package app.fiber.workout;

import jakarta.validation.constraints.NotBlank;

/**
 * {"exerciseId": "..."}: the body of POST /api/sessions/{id}/exercises (add) and
 * PUT /api/sessions/{id}/exercises/{position} (swap), and one item of a new workout's exercise list.
 */
public record AddExerciseRequest(@NotBlank(message = "is required") String exerciseId) {
}
