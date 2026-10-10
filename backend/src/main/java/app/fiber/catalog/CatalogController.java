package app.fiber.catalog;

import app.fiber.auth.AccountUser;
import app.fiber.common.LoginRequiredException;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, read-only catalog: muscle regions, gym equipment and the exercise library.
 * Only GET /api/exercises?mine=true needs a login, because it filters by the user's equipment.
 */
@RestController
@RequestMapping("/api")
public class CatalogController {

	private final CatalogService catalogService;

	public CatalogController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping("/regions")
	public List<RegionResponse> regions() {
		return catalogService.listRegions();
	}

	/** In the same order as the seed file, so screens can show it as is. */
	@GetMapping("/equipment")
	public List<EquipmentResponse> equipment() {
		return catalogService.listEquipment();
	}

	/**
	 * ?q= filters by name (ignoring case); ?all=true also shows retired exercises;
	 * ?mine=true keeps only exercises the logged-in user has the equipment for (401 without a login).
	 *
	 * @param user the logged-in user, or null for a visitor (the endpoint itself is public)
	 */
	@GetMapping("/exercises")
	public List<ExerciseResponse> exercises(
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "false") boolean all,
			@RequestParam(defaultValue = "false") boolean mine,
			@AuthenticationPrincipal AccountUser user) {
		if (!mine) {
			return catalogService.listExercises(q, all);
		}
		if (user == null) {
			throw new LoginRequiredException();
		}
		return catalogService.listExercisesForUser(user.getId(), q, all);
	}

	/** 404 {"error": "..."} if the id is unknown (see ApiExceptionHandler). */
	@GetMapping("/exercises/{id}")
	public ExerciseDetailResponse exercise(@PathVariable String id) {
		return catalogService.getExercise(id);
	}

}
