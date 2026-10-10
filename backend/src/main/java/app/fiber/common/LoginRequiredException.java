package app.fiber.common;

/**
 * Thrown when a public endpoint is asked for something personal by a visitor who isn't logged in
 * (e.g. "only exercises for my equipment"). Becomes a 401 {"error": "Login required"},
 * the same answer as for endpoints that always need a login (see ApiExceptionHandler).
 */
public class LoginRequiredException extends RuntimeException {

	public LoginRequiredException() {
		super("Login required");
	}

}
