package app.fiber.common;

/**
 * Error body from the API contract, e.g. {"error": "Exercise not found: foo"}.
 * The contract's optional "fields" map is added when validation errors arrive.
 */
public record ErrorResponse(String error) {
}
