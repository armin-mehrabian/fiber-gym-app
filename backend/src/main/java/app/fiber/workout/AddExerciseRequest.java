package app.fiber.workout;

import jakarta.validation.constraints.NotBlank;

/** Body of POST /api/sessions/{id}/exercises. */
public record AddExerciseRequest(@NotBlank(message = "is required") String exerciseId) {
}
