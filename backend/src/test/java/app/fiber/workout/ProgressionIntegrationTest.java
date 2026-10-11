package app.fiber.workout;

import static app.fiber.TestWorkouts.addExercise;
import static app.fiber.TestWorkouts.chooseSplit;
import static app.fiber.TestWorkouts.finish;
import static app.fiber.TestWorkouts.finishAt;
import static app.fiber.TestWorkouts.logSet;
import static app.fiber.TestWorkouts.logWorkingSets;
import static app.fiber.TestWorkouts.splitDayId;
import static app.fiber.TestWorkouts.startWorkout;
import static org.assertj.core.api.Assertions.assertThat;

import app.fiber.TestBrowser;
import app.fiber.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.json.JsonComparator;

/**
 * Weight suggestions and "last time" on workout exercises, starting a workout with exercises, and swapping.
 * Test library rep ranges: incline_barbell_press 6-10, single_arm_cable_row 10-12, push_up 8-20, cable_fly 10-15.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ProgressionIntegrationTest {

	private static final ZoneId BERLIN = ZoneId.of(TestBrowser.TIMEZONE);
	private static final JsonComparator STRICT = JsonAssert.comparator(JsonCompareMode.STRICT);

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private TestBrowser browser;

	@BeforeEach
	void signUp() throws Exception {
		browser = new TestBrowser(port);
		browser.signUpNewUser();
	}

	// ---- suggestions and last time ----

	@Test
	void eachReasonComesFromTheLastFinishedWorkout() throws Exception {
		Instant twoDaysAgo = Instant.now().minus(Duration.ofDays(2));
		String last = startWorkout(browser);
		addExercise(browser, last, "incline_barbell_press");
		logSet(browser, last, 1, "100", 1, true, true); // warm-up: never counts
		logWorkingSets(browser, last, 1, "60", 10, 10, 10); // top of 6-10 every time
		logSet(browser, last, 1, "60", 3, false, false); // not ticked: doesn't count
		addExercise(browser, last, "single_arm_cable_row");
		logWorkingSets(browser, last, 2, "30", 12, 11, 10);
		addExercise(browser, last, "push_up");
		logWorkingSets(browser, last, 3, "0", 15, 12);
		finishAt(browser, jdbcTemplate, last, twoDaysAgo, twoDaysAgo.plus(Duration.ofHours(1)));

		String id = startWorkout(browser);
		for (String exercise : List.of("incline_barbell_press", "single_arm_cable_row", "push_up", "cable_fly")) {
			addExercise(browser, id, exercise);
		}
		String session = browser.get("/api/sessions/" + id).body();

		assertSuggestion(session, 1, 62.5, 6, "INCREASE");
		assertSuggestion(session, 2, 30.0, 11, "REPEAT");
		assertSuggestion(session, 3, 0.0, 16, "MORE_REPS");
		assertSuggestion(session, 4, null, 15, "NO_HISTORY");
		String date = LocalDate.ofInstant(twoDaysAgo, BERLIN).toString();
		STRICT.assertIsMatch("""
			{"date": "%s", "sets": [{"weightKg": 60.00, "reps": 10}, {"weightKg": 60.00, "reps": 10},
			 {"weightKg": 60.00, "reps": 10}]}""".formatted(date),
			lastTimeJson(session, 1));
		STRICT.assertIsMatch("""
			{"date": "%s", "sets": [{"weightKg": 0.00, "reps": 15}, {"weightKg": 0.00, "reps": 12}]}"""
			.formatted(date), lastTimeJson(session, 3));
		assertThat(JsonPath.<Object>read(session, "$.exercises[3].lastTime")).isNull();
	}

	@Test
	void theNewestFinishedWorkoutWithTheExerciseCounts() throws Exception {
		finishedWorkoutWith("incline_barbell_press", Duration.ofDays(3), "50", 10, 10, 10);
		finishedWorkoutWith("incline_barbell_press", Duration.ofDays(1), "60", 8, 7);

		String session = addToNewWorkout("incline_barbell_press");

		assertSuggestion(session, 1, 60.0, 8, "REPEAT");
		assertThat(JsonPath.<String>read(session, "$.exercises[0].lastTime.date"))
			.isEqualTo(LocalDate.ofInstant(Instant.now().minus(Duration.ofDays(1)), BERLIN).toString());
	}

	@Test
	void aWorkoutWhereTheExerciseWasNotReallyDoneDoesNotHideTheLastTime() throws Exception {
		finishedWorkoutWith("incline_barbell_press", Duration.ofDays(3), "60", 10, 10, 10);
		// Yesterday: added, but only a warm-up was ticked.
		String yesterday = startWorkout(browser);
		addExercise(browser, yesterday, "incline_barbell_press");
		logSet(browser, yesterday, 1, "20", 10, true, true);
		Instant start = Instant.now().minus(Duration.ofDays(1));
		finishAt(browser, jdbcTemplate, yesterday, start, start.plus(Duration.ofHours(1)));

		String session = addToNewWorkout("incline_barbell_press");

		assertSuggestion(session, 1, 62.5, 6, "INCREASE");
		assertThat(JsonPath.<String>read(session, "$.exercises[0].lastTime.date"))
			.isEqualTo(LocalDate.ofInstant(Instant.now().minus(Duration.ofDays(3)), BERLIN).toString());
	}

	@Test
	void lastTimeIsOnlyFilledWhileTheWorkoutIsInProgress() throws Exception {
		finishedWorkoutWith("cable_fly", Duration.ofDays(2), "10", 12, 12);
		String id = startWorkout(browser);
		String active = addExercise(browser, id, "cable_fly");
		assertThat(JsonPath.<Object>read(active, "$.exercises[0].lastTime")).isNotNull();

		HttpResponse<String> finished = browser.post("/api/sessions/" + id + "/finish", "");

		assertThat(JsonPath.<Object>read(finished.body(), "$.exercises[0].lastTime")).isNull();
		assertThat(JsonPath.<Object>read(browser.get("/api/sessions/" + id).body(), "$.exercises[0].lastTime"))
			.isNull();
		// The stored suggestion stays with the finished workout.
		assertSuggestion(finished.body(), 1, 10.0, 13, "REPEAT");
	}

	@Test
	void anotherUsersWorkoutsAreNeverUsed() throws Exception {
		TestBrowser other = new TestBrowser(port);
		other.signUpNewUser();
		String theirs = startWorkout(other);
		addExercise(other, theirs, "push_up");
		logWorkingSets(other, theirs, 1, "0", 20);
		finish(other, theirs);

		String session = addToNewWorkout("push_up");

		assertSuggestion(session, 1, null, 20, "NO_HISTORY");
		assertThat(JsonPath.<Object>read(session, "$.exercises[0].lastTime")).isNull();
	}

	// ---- starting with exercises ----

	@Test
	void aWorkoutCanStartWithItsSplitDayAndExercises() throws Exception {
		String pushId = splitDayId(chooseSplit(browser, "PPL", "cables", "barbell", "bench"), "Push");
		finishedWorkoutWith("push_up", Duration.ofDays(2), "0", 12);

		HttpResponse<String> started = browser.post("/api/sessions", """
			{"splitDayId": "%s", "exercises": [{"exerciseId": "incline_barbell_press"}, {"exerciseId": "push_up"}]}"""
			.formatted(pushId));

		assertThat(started.statusCode()).isEqualTo(201);
		String json = started.body();
		assertThat(JsonPath.<String>read(json, "$.name")).isEqualTo("Push");
		assertThat(JsonPath.<String>read(json, "$.splitDayId")).isEqualTo(pushId);
		assertThat(JsonPath.<List<Integer>>read(json, "$.exercises[*].position")).containsExactly(1, 2);
		assertThat(JsonPath.<List<String>>read(json, "$.exercises[*].exerciseId"))
			.containsExactly("incline_barbell_press", "push_up");
		assertThat(JsonPath.<List<Integer>>read(json, "$.exercises[*].targetSets")).containsExactly(3, 3);
		assertSuggestion(json, 1, null, 10, "NO_HISTORY");
		assertSuggestion(json, 2, 0.0, 13, "MORE_REPS");
		assertThat(JsonPath.<Integer>read(json, "$.exercises[1].lastTime.sets.length()")).isEqualTo(1);
	}

	@Test
	void anEmptyExerciseListStartsAnEmptyWorkout() throws Exception {
		HttpResponse<String> started = browser.post("/api/sessions", "{\"exercises\": []}");

		assertThat(started.statusCode()).isEqualTo(201);
		assertThat(JsonPath.<List<Object>>read(started.body(), "$.exercises")).isEmpty();
		assertThat(JsonPath.<Object>read(started.body(), "$.splitDayId")).isNull();
	}

	@Test
	void unknownOrMissingExercisesStartNothing() throws Exception {
		HttpResponse<String> unknown = browser.post("/api/sessions",
			"{\"exercises\": [{\"exerciseId\": \"push_up\"}, {\"exerciseId\": \"nope\"}]}");
		HttpResponse<String> missing = browser.post("/api/sessions", "{\"exercises\": [{}]}");
		HttpResponse<String> nullItem = browser.post("/api/sessions", "{\"exercises\": [null]}");

		assertThat(unknown.statusCode()).isEqualTo(400);
		assertThat(unknown.body())
			.isEqualTo("{\"error\":\"Invalid input\",\"fields\":{\"exercises[1].exerciseId\":\"unknown exercise\"}}");
		assertThat(missing.statusCode()).isEqualTo(400);
		assertThat(JsonPath.<Map<String, String>>read(missing.body(), "$.fields"))
			.containsEntry("exercises[0].exerciseId", "is required");
		assertThat(nullItem.statusCode()).isEqualTo(400);
		assertThat(JsonPath.<Map<String, String>>read(nullItem.body(), "$.fields")).containsValue("is required");
		assertThat(browser.get("/api/sessions/active").statusCode()).isEqualTo(204);
	}

	@Test
	void aWorkoutStartsWithAtMost20Exercises() throws Exception {
		String twenty = String.join(",", Collections.nCopies(20, "{\"exerciseId\": \"push_up\"}"));

		HttpResponse<String> tooMany = browser.post("/api/sessions", "{\"exercises\": [" + twenty + ", "
			+ "{\"exerciseId\": \"push_up\"}]}");
		assertThat(tooMany.statusCode()).isEqualTo(400);
		assertThat(tooMany.body())
			.isEqualTo("{\"error\":\"Invalid input\",\"fields\":{\"exercises\":\"must have at most 20 exercises\"}}");
		assertThat(browser.get("/api/sessions/active").statusCode()).isEqualTo(204);

		HttpResponse<String> exactly20 = browser.post("/api/sessions", "{\"exercises\": [" + twenty + "]}");
		assertThat(exactly20.statusCode()).isEqualTo(201);
		assertThat(JsonPath.<Integer>read(exactly20.body(), "$.exercises.length()")).isEqualTo(20);
	}

	// ---- swap ----

	@Test
	void swappingReplacesTheExerciseWithItsOwnRangeAndSuggestion() throws Exception {
		finishedWorkoutWith("push_up", Duration.ofDays(2), "0", 18, 15);
		String id = startWorkout(browser);
		addExercise(browser, id, "incline_barbell_press");
		addExercise(browser, id, "single_arm_cable_row");

		HttpResponse<String> swapped = browser.put("/api/sessions/" + id + "/exercises/1",
			"{\"exerciseId\": \"push_up\"}");

		assertThat(swapped.statusCode()).isEqualTo(200);
		String json = swapped.body();
		assertThat(JsonPath.<List<String>>read(json, "$.exercises[*].exerciseId"))
			.containsExactly("push_up", "single_arm_cable_row");
		assertThat(JsonPath.<String>read(json, "$.exercises[0].name")).isEqualTo("Push-up");
		assertThat(JsonPath.<List<Integer>>read(json, "$.exercises[0].repRange")).containsExactly(8, 20);
		assertThat(JsonPath.<Integer>read(json, "$.exercises[0].targetSets")).isEqualTo(3);
		assertSuggestion(json, 1, 0.0, 19, "MORE_REPS");
		assertThat(JsonPath.<Integer>read(json, "$.exercises[0].lastTime.sets.length()")).isEqualTo(2);
		assertSuggestion(json, 2, null, 12, "NO_HISTORY");
		assertThat(browser.get("/api/sessions/" + id).body()).isEqualTo(json);
	}

	@Test
	void anExerciseWithAnySetCantBeSwapped() throws Exception {
		String id = startWorkout(browser);
		addExercise(browser, id, "incline_barbell_press");
		logSet(browser, id, 1, "20", 10, true, false); // even one unticked warm-up

		HttpResponse<String> response = browser.put("/api/sessions/" + id + "/exercises/1",
			"{\"exerciseId\": \"push_up\"}");

		assertThat(response.statusCode()).isEqualTo(409);
		assertThat(response.body()).isEqualTo("{\"error\":\"Remove its sets before swapping\"}");
		assertThat(JsonPath.<String>read(browser.get("/api/sessions/" + id).body(), "$.exercises[0].exerciseId"))
			.isEqualTo("incline_barbell_press");
	}

	@Test
	void badSwapsAreRejected() throws Exception {
		String id = startWorkout(browser);
		addExercise(browser, id, "incline_barbell_press");

		HttpResponse<String> unknownPosition = browser.put("/api/sessions/" + id + "/exercises/2",
			"{\"exerciseId\": \"push_up\"}");
		HttpResponse<String> unknownExercise = browser.put("/api/sessions/" + id + "/exercises/1",
			"{\"exerciseId\": \"nope\"}");
		HttpResponse<String> missingExercise = browser.put("/api/sessions/" + id + "/exercises/1", "{}");

		assertThat(unknownPosition.statusCode()).isEqualTo(404);
		assertThat(unknownPosition.body()).isEqualTo("{\"error\":\"No exercise at position 2 in this workout\"}");
		assertThat(unknownExercise.statusCode()).isEqualTo(400);
		assertThat(unknownExercise.body())
			.isEqualTo("{\"error\":\"Invalid input\",\"fields\":{\"exerciseId\":\"unknown exercise\"}}");
		assertThat(missingExercise.statusCode()).isEqualTo(400);
		assertThat(missingExercise.body())
			.isEqualTo("{\"error\":\"Invalid input\",\"fields\":{\"exerciseId\":\"is required\"}}");
	}

	@Test
	void aFinishedOrSomeoneElsesWorkoutCantBeSwapped() throws Exception {
		String id = startWorkout(browser);
		addExercise(browser, id, "incline_barbell_press");
		TestBrowser other = new TestBrowser(port);
		other.signUpNewUser();

		HttpResponse<String> notMine = other.put("/api/sessions/" + id + "/exercises/1", "{\"exerciseId\": \"push_up\"}");
		assertThat(notMine.statusCode()).isEqualTo(404);
		assertThat(notMine.body()).isEqualTo("{\"error\":\"Workout not found\"}");

		finish(browser, id);
		HttpResponse<String> finished = browser.put("/api/sessions/" + id + "/exercises/1",
			"{\"exerciseId\": \"push_up\"}");
		assertThat(finished.statusCode()).isEqualTo(409);
		assertThat(finished.body()).isEqualTo("{\"error\":\"This workout is finished\"}");
	}

	@Test
	void swappingNeedsALoginAndACsrfToken() throws Exception {
		String id = startWorkout(browser);
		addExercise(browser, id, "incline_barbell_press");
		TestBrowser anonymous = new TestBrowser(port);
		anonymous.get("/api/auth/csrf");

		HttpResponse<String> response = anonymous.put("/api/sessions/" + id + "/exercises/1",
			"{\"exerciseId\": \"push_up\"}");

		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.body()).isEqualTo("{\"error\":\"Login required\"}");
	}

	// ---- helpers ----

	/** A finished workout, {@code ago} in the past, with one exercise and these working sets. */
	private void finishedWorkoutWith(String exerciseId, Duration ago, String weightKg, int... reps) throws Exception {
		String id = startWorkout(browser);
		addExercise(browser, id, exerciseId);
		logWorkingSets(browser, id, 1, weightKg, reps);
		Instant start = Instant.now().minus(ago);
		finishAt(browser, jdbcTemplate, id, start, start.plus(Duration.ofHours(1)));
	}

	/** Starts a workout, adds the exercise and returns the session JSON. */
	private String addToNewWorkout(String exerciseId) throws Exception {
		return addExercise(browser, startWorkout(browser), exerciseId);
	}

	private static String lastTimeJson(String session, int position) {
		return JsonPath.parse((Object) JsonPath.read(session, "$.exercises[" + (position - 1) + "].lastTime"))
			.jsonString();
	}

	private static void assertSuggestion(String session, int position, Double weightKg, int reps, String reason) {
		String path = "$.exercises[" + (position - 1) + "].";
		Number weight = JsonPath.read(session, path + "suggestedWeightKg");
		if (weightKg == null) {
			assertThat(weight).as("weight at " + position).isNull();
		} else {
			assertThat(weight).as("weight at " + position).isNotNull();
			assertThat(weight.doubleValue()).as("weight at " + position).isEqualTo(weightKg);
		}
		assertThat(JsonPath.<Integer>read(session, path + "suggestedReps")).as("reps at " + position).isEqualTo(reps);
		assertThat(JsonPath.<String>read(session, path + "suggestionReason")).as("reason at " + position)
			.isEqualTo(reason);
	}

}
