package app.fiber.workout;

import java.util.UUID;

/** Body of POST /api/sessions. splitDayId is optional; it must be one of the user's split days. */
public record StartSessionRequest(UUID splitDayId) {
}
