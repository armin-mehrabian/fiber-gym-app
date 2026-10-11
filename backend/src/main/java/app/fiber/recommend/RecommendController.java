package app.fiber.recommend;

import app.fiber.auth.AccountUser;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The next workout and swap options. Login required (see SecurityConfig); always the user's own data. */
@RestController
@RequestMapping("/api/recommendation")
public class RecommendController {

	private final RecommendService recommendService;

	public RecommendController(RecommendService recommendService) {
		this.recommendService = recommendService;
	}

	@GetMapping("/next")
	public RecommendationResponse next(@AuthenticationPrincipal AccountUser user) {
		return recommendService.next(user.getId());
	}

	/**
	 * Swap options for {@code exerciseId} on that split day; without a splitDayId every region is allowed.
	 * A missing or blank exerciseId is a 400 {"error": "Invalid input", "fields": {"exerciseId": "is required"}}
	 * (see ApiExceptionHandler); unknown ids are checked by the service.
	 */
	@GetMapping("/alternatives")
	public List<AlternativeResponse> alternatives(@AuthenticationPrincipal AccountUser user,
			@RequestParam(required = false) @NotBlank(message = "is required") String exerciseId,
			@RequestParam(required = false) UUID splitDayId) {
		return recommendService.alternatives(user.getId(), exerciseId, splitDayId);
	}

}
