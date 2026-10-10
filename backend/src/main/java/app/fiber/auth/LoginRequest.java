package app.fiber.auth;

import jakarta.validation.constraints.NotBlank;

/** Body of POST /api/auth/login. */
public record LoginRequest(
	@NotBlank(message = "is required") String email,
	@NotBlank(message = "is required") String password) {
}
