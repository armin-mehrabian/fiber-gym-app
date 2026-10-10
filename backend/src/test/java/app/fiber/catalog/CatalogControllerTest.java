package app.fiber.catalog;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.fiber.auth.SecurityConfig;
import app.fiber.common.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web layer only: the service is mocked. Checks the JSON shapes, the query parameters,
 * the 404 body, and (with our SecurityConfig) that the catalog needs no login.
 */
// Without the database, our own UserDetailsService isn't loaded here, so Spring Boot would
// create a demo user and log a generated password. Not needed: these tests never log in.
@WebMvcTest(controllers = CatalogController.class, excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import(SecurityConfig.class)
class CatalogControllerTest {

	private static final ExerciseResponse ROW = new ExerciseResponse("single_arm_cable_row", "Single-arm cable row",
		List.of("cables"), "horizontal_pull", List.of("lats_lower"), List.of("biceps_long"), List.of(10, 12),
		"Drive the elbow toward your hip.");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CatalogService catalogService;

	@Test
	void regionsHaveTheContractShape() throws Exception {
		when(catalogService.listRegions())
			.thenReturn(List.of(new RegionResponse("lats_upper", "Upper lats", "back", "back", 90)));

		mockMvc.perform(get("/api/regions"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				[{ "id": "lats_upper", "name": "Upper lats", "group": "back", "view": "back", "order": 90 }]""",
				JsonCompareMode.STRICT));
	}

	@Test
	void exercisesHaveTheContractShape() throws Exception {
		when(catalogService.listExercises(null, false)).thenReturn(List.of(ROW));

		mockMvc.perform(get("/api/exercises"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				[{ "id": "single_arm_cable_row", "name": "Single-arm cable row", "equipment": ["cables"],
				   "pattern": "horizontal_pull", "primary": ["lats_lower"], "secondary": ["biceps_long"],
				   "repRange": [10, 12], "cue": "Drive the elbow toward your hip." }]""", JsonCompareMode.STRICT));
	}

	@Test
	void searchAndAllArePassedToTheService() throws Exception {
		mockMvc.perform(get("/api/exercises").param("q", "row").param("all", "true"))
			.andExpect(status().isOk());

		verify(catalogService).listExercises("row", true);
	}

	@Test
	void exerciseDetailAddsNoteBestAndLast() throws Exception {
		when(catalogService.getExercise("single_arm_cable_row")).thenReturn(new ExerciseDetailResponse(
			ROW.id(), ROW.name(), ROW.equipment(), ROW.pattern(), ROW.primary(), ROW.secondary(), ROW.repRange(),
			ROW.cue(), "Weak evidence.", null, null));

		mockMvc.perform(get("/api/exercises/single_arm_cable_row"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{ "id": "single_arm_cable_row", "name": "Single-arm cable row", "equipment": ["cables"],
				  "pattern": "horizontal_pull", "primary": ["lats_lower"], "secondary": ["biceps_long"],
				  "repRange": [10, 12], "cue": "Drive the elbow toward your hip.",
				  "note": "Weak evidence.", "best": null, "last": null }""", JsonCompareMode.STRICT));
	}

	@Test
	void unknownExerciseIs404WithErrorBody() throws Exception {
		when(catalogService.getExercise("nope")).thenThrow(new NotFoundException("Exercise not found: nope"));

		mockMvc.perform(get("/api/exercises/nope"))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
				{ "error": "Exercise not found: nope" }""", JsonCompareMode.STRICT));
	}

	@Test
	void wrongParameterTypeIs400WithErrorBody() throws Exception {
		mockMvc.perform(get("/api/exercises").param("all", "xyz"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
				{ "error": "Invalid value 'xyz' for parameter 'all'" }""", JsonCompareMode.STRICT));
	}

	// The tests above run without a login, so the catalog is public. These must still need one.
	@Test
	void otherEndpointsStillNeedLogin() throws Exception {
		mockMvc.perform(get("/api/settings"))
			.andExpect(status().isUnauthorized());
		// A user's exercise history (Phase 10) is not part of the public catalog.
		mockMvc.perform(get("/api/exercises/single_arm_cable_row/history"))
			.andExpect(status().isUnauthorized());
	}

}
