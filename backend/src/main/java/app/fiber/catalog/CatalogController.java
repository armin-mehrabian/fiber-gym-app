package app.fiber.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, read-only catalog: muscle regions and the exercise library.
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

	/** ?q= filters by name (ignoring case); ?all=true also shows retired exercises. */
	@GetMapping("/exercises")
	public List<ExerciseResponse> exercises(
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "false") boolean all) {
		return catalogService.listExercises(q, all);
	}

	/** 404 {"error": "..."} if the id is unknown (see ApiExceptionHandler). */
	@GetMapping("/exercises/{id}")
	public ExerciseDetailResponse exercise(@PathVariable String id) {
		return catalogService.getExercise(id);
	}

}
