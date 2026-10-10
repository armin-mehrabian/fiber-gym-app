package app.fiber.auth;

import java.util.UUID;

/** The logged-in user, as returned by signup, login and GET /api/me. */
public record UserResponse(UUID id, String email, boolean onboarded) {
}
