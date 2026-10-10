package app.fiber.catalog;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.fiber.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The catalog endpoints end to end: fixture seed files -> database -> JSON.
 * Needs Docker (Testcontainers). No login is used anywhere, which proves the endpoints are public.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void regionsAreSortedByOrder() throws Exception {
		mockMvc.perform(get("/api/regions"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].id").value(contains(
				"chest_upper", "chest_mid", "delt_front", "lats_upper", "lats_lower", "biceps_long")))
			.andExpect(jsonPath("$[0].name").value("Upper chest"))
			.andExpect(jsonPath("$[0].group").value("chest"))
			.andExpect(jsonPath("$[0].view").value("front"))
			.andExpect(jsonPath("$[0].order").value(10));
	}

	@Test
	void exercisesAreSortedByNameAndRetiredOnesAreHidden() throws Exception {
		mockMvc.perform(get("/api/exercises"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name").value(contains(
				"Cable fly", "Incline barbell press", "Push-up", "Single-arm cable row")));
	}

	@Test
	void allTrueAlsoShowsRetiredExercises() throws Exception {
		mockMvc.perform(get("/api/exercises").param("all", "true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].id").value(contains(
				"barbell_floor_press", "cable_fly", "incline_barbell_press", "push_up", "single_arm_cable_row")));
	}

	@Test
	void qFiltersByNameIgnoringCase() throws Exception {
		mockMvc.perform(get("/api/exercises").param("q", "CABLE"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].id").value(contains("cable_fly", "single_arm_cable_row")));
	}

	@Test
	void qAndAllWorkTogether() throws Exception {
		mockMvc.perform(get("/api/exercises").param("q", "press").param("all", "true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].id").value(contains("barbell_floor_press", "incline_barbell_press")));
	}

	@Test
	void listItemHasTheContractShape() throws Exception {
		mockMvc.perform(get("/api/exercises").param("q", "incline"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				[{ "id": "incline_barbell_press", "name": "Incline barbell press", "equipment": ["barbell", "bench"],
				   "pattern": "horizontal_push", "primary": ["chest_upper", "chest_mid"], "secondary": ["delt_front"],
				   "repRange": [6, 10], "cue": "Lower the bar to your upper chest." }]""", JsonCompareMode.STRICT));
	}

	// The fixture lists chest_upper before chest_mid; the first primary is the main region,
	// so the API must keep the file order, not sort alphabetically.
	@Test
	void primaryAndSecondaryKeepTheOrderFromTheSeedFile() throws Exception {
		mockMvc.perform(get("/api/exercises/incline_barbell_press"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.primary").value(contains("chest_upper", "chest_mid")));
		mockMvc.perform(get("/api/exercises/single_arm_cable_row"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.secondary").value(contains("lats_upper", "biceps_long")));
	}

	@Test
	void exerciseDetailIsTheListItemPlusNoteBestAndLast() throws Exception {
		mockMvc.perform(get("/api/exercises/single_arm_cable_row"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{ "id": "single_arm_cable_row", "name": "Single-arm cable row", "equipment": ["cables"],
				  "pattern": "horizontal_pull", "primary": ["lats_lower"], "secondary": ["lats_upper", "biceps_long"],
				  "repRange": [10, 12], "cue": "Drive the elbow toward your hip, not straight back.",
				  "note": "Upper vs lower lat emphasis is weak evidence.", "best": null, "last": null }""",
				JsonCompareMode.STRICT));
	}

	@Test
	void bodyweightExerciseHasEmptyEquipmentAndNullNote() throws Exception {
		mockMvc.perform(get("/api/exercises/push_up"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.equipment").isEmpty())
			.andExpect(jsonPath("$.note").value(nullValue()));
	}

	// Old workouts may point to a retired exercise, so its page must still open.
	@Test
	void retiredExerciseCanStillBeOpened() throws Exception {
		mockMvc.perform(get("/api/exercises/barbell_floor_press"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Barbell floor press"));
	}

	@Test
	void unknownExerciseIs404WithErrorBody() throws Exception {
		mockMvc.perform(get("/api/exercises/does_not_exist"))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
				{ "error": "Exercise not found: does_not_exist" }""", JsonCompareMode.STRICT));
	}

}
