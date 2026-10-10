package app.fiber.auth;

import app.fiber.common.ValidTimezone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/auth/signup. The timezone comes from the browser.
 * 72 is the longest password BCrypt can hash.
 */
public record SignupRequest(
	@NotBlank(message = "is required")
	@Email(message = "must be a valid email address")
	String email,

	@NotNull(message = "is required")
	@Size(min = 8, max = 72, message = "must be 8 to 72 characters")
	String password,

	@NotBlank(message = "is required")
	@ValidTimezone
	String timezone) {
}
