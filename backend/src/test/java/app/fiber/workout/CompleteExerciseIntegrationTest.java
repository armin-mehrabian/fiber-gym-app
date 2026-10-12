package app.fiber.workout;

import static app.fiber.TestWorkouts.addExercise;
import static app.fiber.TestWorkouts.finish;
import static app.fiber.TestWorkouts.logSet;
import static app.fiber.TestWorkouts.startWorkout;
import static org.assertj.core.api.Assertions.assertThat;

import app.fiber.TestBrowser;
import app.fiber.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * POST /api/sessions/{id}/exercises/{position}/complete end to end: one tap logs a whole exercise
 * as completed working sets. Fixture exercises: incline_barbell_press (primary chest_upper,
 * chest_mid; secondary delt_front) and push_up.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CompleteExerciseIntegrationTest {

	private static final String FINISHED = "{\"error\":\"This workout is finished\"}";
	private static final String WORKOUT_NOT_FOUND = "{\"error\":\"Workout not found\"}";

	@LocalServerPort
	private int port;

	private TestBrowser browser;
	private String sessionId;

	@BeforeEach
	void aWorkoutWithTwoExercises() throws Exception {
		browser = new TestBrowser(port);
		browser.signUpNewUser();
		sessionId = startWorkout(browser);
		addExercise(browser, sessionId, "incline_barbell_press");
		addExercise(browser, sessionId, "push_up");
	}

	@Test
	void oneTapAddsCompletedWorkingSetsAfterTheExistingOnes() throws Exception {
		// A warm-up logged by hand first: the new sets come after it.
		logSet(browser, sessionId, 1, "20", 12, true, true);

		HttpResponse<String> response = complete(1, "{\"weightKg\": 60, \"reps\": 10, \"sets\": 3}");

		assertThat(response.statusCode()).isEqualTo(200);
		String json = response.body();
		assertThat(JsonPath.<String>read(json, "$.id")).isEqualTo(sessionId);
		assertThat(JsonPath.<String>read(json, "$.status")).isEqualTo("ACTIVE");
		assertThat(JsonPath.<List<Integer>>read(json, "$.exercises[0].sets[*].setNumber")).containsExactly(1, 2, 3, 4);
		assertThat(JsonPath.<List<Double>>read(json, "$.exercises[0].sets[*].weightKg"))
			.containsExactly(20.0, 60.0, 60.0, 60.0);
		assertThat(JsonPath.<List<Integer>>read(json, "$.exercises[0].sets[*].reps")).containsExactly(12, 10, 10, 10);
		assertThat(JsonPath.<List<Boolean>>read(json, "$.exercises[0].sets[*].isWarmup"))
			.containsExactly(true, false, false, false);
		assertThat(JsonPath.<List<Boolean>>read(json, "$.exercises[0].sets[*].completed")).containsOnly(true);
		assertThat(JsonPath.<List<String>>read(json, "$.exercises[0].sets[*].id")).doesNotContainNull()
			.doesNotHaveDuplicates();
		// The other exercise is untouched.
		assertThat(JsonPath.<List<Object>>read(json, "$.exercises[1].sets")).isEmpty();

		// It is saved: reading the workout again shows the same sets.
		String saved = browser.get("/api/sessions/" + sessionId).body();
		assertThat(JsonPath.<List<Object>>read(saved, "$.exercises[0].sets"))
			.isEqualTo(JsonPath.<List<Object>>read(json, "$.exercises[0].sets"));
	}

	@Test
	void tappingAgainAppendsMore() throws Exception {
		complete(2, "{\"weightKg\": 0, \"reps\": 15, \"sets\": 2}");

		String json = complete(2, "{\"weightKg\": 0, \"reps\": 12, \"sets\": 1}").body();

		assertThat(JsonPath.<List<Integer>>read(json, "$.exercises[1].sets[*].setNumber")).containsExactly(1, 2, 3);
		assertThat(JsonPath.<List<Integer>>read(json, "$.exercises[1].sets[*].reps")).containsExactly(15, 15, 12);
	}

	@Test
	void theSetsCountForCoverageOnceTheWorkoutIsFinished() throws Exception {
		complete(1, "{\"weightKg\": 60, \"reps\": 10, \"sets\": 4}");
		finish(browser, sessionId);

		String coverage = browser.get("/api/coverage/week").body();

		assertThat(JsonPath.<List<Double>>read(coverage, "$.regions[?(@.regionId == 'chest_upper')].effective"))
			.containsExactly(4.0);
		assertThat(JsonPath.<List<Double>>read(coverage, "$.regions[?(@.regionId == 'delt_front')].effective"))
			.containsExactly(2.0);
	}

	@Test
	void oneAndTenSetsAreTheLimitsAndTheWeightAndRepLimitsAreAllowed() throws Exception {
		assertThat(complete(1, "{\"weightKg\": 0, \"reps\": 100, \"sets\": 1}").statusCode()).isEqualTo(200);

		HttpResponse<String> ten = complete(1, "{\"weightKg\": 1000, \"reps\": 0, \"sets\": 10}");

		assertThat(ten.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<List<Object>>read(ten.body(), "$.exercises[0].sets")).hasSize(11);
	}

	@Test
	void valuesOutsideTheLimitsAre400WithFieldsAndAddNothing() throws Exception {
		assertThat(fieldsOf(complete(1, "{\"weightKg\": -0.5, \"reps\": 101, \"sets\": 0}"))).containsOnly(
			Map.entry("weightKg", "must be between 0 and 1000"), Map.entry("reps", "must be between 0 and 100"),
			Map.entry("sets", "must be between 1 and 10"));
		assertThat(fieldsOf(complete(1, "{\"weightKg\": 1000.01, \"reps\": -1, \"sets\": 11}"))).containsOnly(
			Map.entry("weightKg", "must be between 0 and 1000"), Map.entry("reps", "must be between 0 and 100"),
			Map.entry("sets", "must be between 1 and 10"));
		assertThat(fieldsOf(complete(1, "{\"weightKg\": 12.345, \"reps\": 5, \"sets\": 3}"))).containsOnly(
			Map.entry("weightKg", "must have at most 2 decimals"));
		assertThat(fieldsOf(complete(1, "{}"))).containsOnly(
			Map.entry("weightKg", "is required"), Map.entry("reps", "is required"), Map.entry("sets", "is required"));

		HttpResponse<String> malformed = complete(1, "{\"weightKg\": 60, \"reps\": 10, \"sets\": ");
		assertThat(malformed.statusCode()).isEqualTo(400);
		assertThat(malformed.body()).isEqualTo("{\"error\":\"Malformed request body\"}");

		assertThat(JsonPath.<List<Object>>read(browser.get("/api/sessions/" + sessionId).body(),
			"$.exercises[*].sets[*]")).isEmpty();
	}

	@Test
	void aFinishedWorkoutIs409() throws Exception {
		finish(browser, sessionId);

		HttpResponse<String> response = complete(1, "{\"weightKg\": 60, \"reps\": 10, \"sets\": 3}");

		assertThat(response.statusCode()).isEqualTo(409);
		assertThat(response.body()).isEqualTo(FINISHED);
		assertThat(JsonPath.<List<Object>>read(browser.get("/api/sessions/" + sessionId).body(),
			"$.exercises[*].sets[*]")).isEmpty();
	}

	@Test
	void someoneElsesOrAnUnknownWorkoutIs404() throws Exception {
		TestBrowser other = new TestBrowser(port);
		other.signUpNewUser();
		String body = "{\"weightKg\": 60, \"reps\": 10, \"sets\": 3}";

		for (String id : List.of(sessionId, "00000000-0000-0000-0000-000000000000")) {
			HttpResponse<String> response = other.post("/api/sessions/" + id + "/exercises/1/complete", body);
			assertThat(response.statusCode()).isEqualTo(404);
			assertThat(response.body()).isEqualTo(WORKOUT_NOT_FOUND);
		}
		// The owner's workout is unchanged.
		assertThat(JsonPath.<List<Object>>read(browser.get("/api/sessions/" + sessionId).body(),
			"$.exercises[*].sets[*]")).isEmpty();
	}

	@Test
	void anUnknownPositionIs404() throws Exception {
		HttpResponse<String> response = complete(3, "{\"weightKg\": 60, \"reps\": 10, \"sets\": 3}");

		assertThat(response.statusCode()).isEqualTo(404);
		assertThat(response.body()).isEqualTo("{\"error\":\"No exercise at position 3 in this workout\"}");
	}

	@Test
	void aLoginIsNeeded() throws Exception {
		TestBrowser anonymous = new TestBrowser(port);
		anonymous.get("/api/auth/csrf"); // a valid CSRF token, but no login

		HttpResponse<String> response = anonymous.post("/api/sessions/" + sessionId + "/exercises/1/complete",
			"{\"weightKg\": 60, \"reps\": 10, \"sets\": 3}");

		assertThat(response.statusCode()).isEqualTo(401);
	}

	private HttpResponse<String> complete(int position, String body) throws Exception {
		return browser.post("/api/sessions/" + sessionId + "/exercises/" + position + "/complete", body);
	}

	private static Map<String, String> fieldsOf(HttpResponse<String> response) {
		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(JsonPath.<String>read(response.body(), "$.error")).isEqualTo("Invalid input");
		return JsonPath.read(response.body(), "$.fields");
	}

}
