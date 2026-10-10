package app.fiber.settings;

import app.fiber.auth.AccountUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The logged-in user's settings and weekly targets. Login required (see SecurityConfig).
 * The user always comes from the session, never from the request.
 */
@RestController
@RequestMapping("/api")
public class SettingsController {

	private final SettingsService settingsService;

	public SettingsController(SettingsService settingsService) {
		this.settingsService = settingsService;
	}

	@GetMapping("/settings")
	public SettingsResponse settings(@AuthenticationPrincipal AccountUser user) {
		return settingsService.getSettings(user.getId());
	}

	@PutMapping("/settings")
	public SettingsResponse updateSettings(@AuthenticationPrincipal AccountUser user,
			@Valid @RequestBody SettingsRequest body) {
		return settingsService.updateSettings(user.getId(), body);
	}

	@GetMapping("/targets")
	public List<TargetResponse> targets(@AuthenticationPrincipal AccountUser user) {
		return settingsService.getTargets(user.getId());
	}

	/** Any subset of regions; the answer is always the full list. */
	@PutMapping("/targets")
	public List<TargetResponse> updateTargets(@AuthenticationPrincipal AccountUser user,
			@RequestBody List<TargetRequest> body) {
		return settingsService.updateTargets(user.getId(), body);
	}

}
