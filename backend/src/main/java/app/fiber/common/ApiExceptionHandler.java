package app.fiber.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns exceptions thrown by any controller into the API's error body: {"error": "message"}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(NotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ErrorResponse notFound(NotFoundException e) {
		return new ErrorResponse(e.getMessage());
	}

	/** A request value of the wrong type, e.g. ?all=xyz where true/false is expected. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse badParameter(MethodArgumentTypeMismatchException e) {
		return new ErrorResponse("Invalid value '" + e.getValue() + "' for parameter '" + e.getName() + "'");
	}

}
