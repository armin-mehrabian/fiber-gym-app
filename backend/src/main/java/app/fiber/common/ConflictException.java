package app.fiber.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thrown by services when the request clashes with existing data (e.g. an email that is
 * already registered). Becomes a 409 with the clashing fields (see ApiExceptionHandler).
 */
public class ConflictException extends RuntimeException {

	private final Map<String, String> fields;

	public ConflictException(String message, Map<String, String> fields) {
		super(message);
		// A copy that keeps the order the fields were added in, so errors read top to bottom.
		this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
	}

	public Map<String, String> getFields() {
		return fields;
	}

}
