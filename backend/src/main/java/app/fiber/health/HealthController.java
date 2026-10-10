package app.fiber.health;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public health check, used by Docker, CI and the frontend shell.
 */
@RestController
public class HealthController {

	private final HealthService healthService;

	public HealthController(HealthService healthService) {
		this.healthService = healthService;
	}

	/** 200 when the database answers, 503 (Service Unavailable) when it doesn't. */
	@GetMapping("/api/health")
	public ResponseEntity<HealthResponse> health() {
		if (healthService.isDatabaseUp()) {
			return ResponseEntity.ok(HealthResponse.up());
		}
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(HealthResponse.down());
	}

}
