package app.fiber.common;

/**
 * Thrown by services when something asked for doesn't exist. Becomes a 404 (see ApiExceptionHandler).
 */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}

}
