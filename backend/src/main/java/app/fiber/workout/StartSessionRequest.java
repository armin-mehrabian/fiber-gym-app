package app.fiber.workout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Body of POST /api/sessions. Both fields are optional.
 * splitDayId must be one of the user's split days. exercises are added in this order, each like
 * POST /api/sessions/{id}/exercises (e.g. the recommended workout). At most 20, which is far more
 * than one workout needs and keeps a single request from creating hundreds of rows.
 */
public record StartSessionRequest(
	UUID splitDayId,
	@Size(max = 20, message = "must have at most 20 exercises")
	List<@NotNull(message = "is required") @Valid AddExerciseRequest> exercises) {
}
