package app.fiber.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Error body of the API, e.g. {"error": "Exercise not found: foo"}.
 * For invalid input it also names the bad fields:
 * {"error": "Invalid input", "fields": {"email": "must be a valid email address"}}.
 * "fields" is left out of the JSON when there are none.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String error, Map<String, String> fields) {

	public ErrorResponse(String error) {
		this(error, null);
	}

}
