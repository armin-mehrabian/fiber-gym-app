package app.fiber.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns exceptions thrown by any controller into the API's error body:
 * {"error": "message"}, plus "fields" for invalid input.
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

	/** A request body that failed its @Valid checks. Lists one message per bad field. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse invalidBody(MethodArgumentNotValidException e) {
		Map<String, String> fields = new LinkedHashMap<>();
		for (FieldError error : e.getBindingResult().getFieldErrors()) {
			fields.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		return new ErrorResponse("Invalid input", fields);
	}

	/** Invalid input found by a service, e.g. an unknown equipment id. */
	@ExceptionHandler(BadRequestException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse badRequest(BadRequestException e) {
		return new ErrorResponse(e.getMessage(), e.getFields());
	}

	/** A body that isn't valid JSON, or has the wrong shape (e.g. an object where a list is expected). */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse unreadableBody(HttpMessageNotReadableException e) {
		return new ErrorResponse("Malformed request body");
	}

	/**
	 * The request clashes with existing data, e.g. an email that is already registered.
	 * "fields" is left out when the conflict isn't about a particular field.
	 */
	@ExceptionHandler(ConflictException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ErrorResponse conflict(ConflictException e) {
		return new ErrorResponse(e.getMessage(), e.getFields().isEmpty() ? null : e.getFields());
	}

	/** A public endpoint was asked for something that needs a login, e.g. GET /api/exercises?mine=true. */
	@ExceptionHandler(LoginRequiredException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ErrorResponse loginRequired(LoginRequiredException e) {
		return new ErrorResponse(e.getMessage());
	}

	/**
	 * Login failed. Spring Security throws this both for an unknown email and for a wrong password,
	 * and we answer both the same way, so nobody can find out which emails have an account.
	 */
	@ExceptionHandler(BadCredentialsException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ErrorResponse wrongCredentials(BadCredentialsException e) {
		return new ErrorResponse("Wrong email or password");
	}

}
