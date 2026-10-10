package app.fiber.health;

/**
 * Body of GET /api/health, e.g. {"status":"ok","database":"up"}.
 */
public record HealthResponse(String status, String database) {

	public static HealthResponse up() {
		return new HealthResponse("ok", "up");
	}

	public static HealthResponse down() {
		return new HealthResponse("degraded", "down");
	}

}
