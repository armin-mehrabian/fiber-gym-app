package app.fiber.coverage;

import app.fiber.auth.AccountUser;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Weekly coverage per region. Login required (see SecurityConfig); always the logged-in user's own data. */
@RestController
public class CoverageController {

	private final CoverageService coverageService;

	public CoverageController(CoverageService coverageService) {
		this.coverageService = coverageService;
	}

	/**
	 * The Monday-Sunday week that contains {@code date} (YYYY-MM-DD), or the current week if it is missing.
	 * A date that isn't a real YYYY-MM-DD date (e.g. 2026-02-30) answers 400.
	 */
	@GetMapping("/api/coverage/week")
	public CoverageWeekResponse week(@AuthenticationPrincipal AccountUser user,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return coverageService.week(user.getId(), date);
	}

}
