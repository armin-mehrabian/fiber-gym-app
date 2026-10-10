package app.fiber.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thrown by services when the input is invalid in a way only the service can check
 * (e.g. an equipment id that doesn't exist). Becomes a 400 with the bad fields (see ApiExceptionHandler).
 */
public class BadRequestException extends RuntimeException {

	private final Map<String, String> fields;

	public BadRequestException(String message, Map<String, String> fields) {
		super(message);
		// A copy that keeps the order the fields were added in, so errors read top to bottom.
		this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
	}

	public Map<String, String> getFields() {
		return fields;
	}

}
