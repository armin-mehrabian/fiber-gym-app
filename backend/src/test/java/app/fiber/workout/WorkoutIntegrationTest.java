package app.fiber.workout;

import static app.fiber.TestBrowser.PASSWORD;
import static app.fiber.TestBrowser.loginJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.fiber.TestBrowser;
import app.fiber.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.json.JsonComparator;
import org.springframework.test.json.JsonAssert;

/**
 * Workout logging end to end: real HTTP with a logged-in session (and CSRF tokens), against a real
 * PostgreSQL (Testcontainers, needs Docker). Each test signs up fresh users.
 *
 * Fixture exercises used here: incline_barbell_press (primary chest_upper, chest_mid; secondary
 * delt_front; 6-10 reps), single_arm_cable_row (primary lats_lower; secondary lats_upper,
 * biceps_long; 10-12 reps), cable_fly and push_up.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class WorkoutIntegrationTest {

	private static final String ALREADY_ACTIVE = "{\"error\":\"A workout is already in progress\"}";
	private static final String FINISHED = "{\"error\":\"This workout is finished\"}";
	private static final String WORKOUT_NOT_FOUND = "{\"error\":\"Workout not found\"}";
	private static final String SET_NOT_FOUND = "{\"error\":\"Set not found\"}";
	private static final JsonComparator STRICT = JsonAssert.comparator(JsonCompareMode.STRICT);

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private WorkoutSessionRepository sessionRepository;

	private TestBrowser browser;

	@BeforeEach
	void signUp() throws Exception {
		browser = new TestBrowser(port);
		browser.signUpNewUser();
	}

	// ---- the whole flow ----

	@Test
	void startAddExercisesLogSetsFinishAndSeeItInHistory() throws Exception {
		// Start: an empty, active workout called "Workout".
		HttpResponse<String> started = browser.post("/api/sessions", "{}");
		assertThat(started.statusCode()).isEqualTo(201);
		String id = JsonPath.read(started.body(), "$.id");
		assertThat(JsonPath.<String>read(started.body(), "$.name")).isEqualTo("Workout");
		assertThat(JsonPath.<String>read(started.body(), "$.status")).isEqualTo("ACTIVE");
		assertThat(Instant.parse(JsonPath.read(started.body(), "$.startedAt"))).isBefore(Instant.now().plusSeconds(1));
		assertThat(JsonPath.<Object>read(started.body(), "$.finishedAt")).isNull();
		assertThat(JsonPath.<List<Object>>read(started.body(), "$.exercises")).isEmpty();

		// Two exercises, appended in order, with library details and the default plan.
		HttpResponse<String> first = browser.post("/api/sessions/" + id + "/exercises",
			"{\"exerciseId\": \"incline_barbell_press\"}");
		assertThat(first.statusCode()).isEqualTo(200);
		String second = addExercise(id, "single_arm_cable_row");
		STRICT.assertIsMatch("""
			{"id": "%s", "name": "Workout", "splitDayId": null, "status": "ACTIVE", "startedAt": "%s",
			 "finishedAt": null,
			 "exercises": [{"position": 1, "exerciseId": "incline_barbell_press", "name": "Incline barbell press",
			  "primary": ["chest_upper", "chest_mid"], "secondary": ["delt_front"], "targetSets": 3,
			  "repRange": [6, 10], "suggestedWeightKg": null, "suggestedReps": 10, "suggestionReason": "NO_HISTORY",
			  "lastTime": null, "sets": []},
			 {"position": 2, "exerciseId": "single_arm_cable_row", "name": "Single-arm cable row",
			  "primary": ["lats_lower"], "secondary": ["lats_upper", "biceps_long"], "targetSets": 3,
			  "repRange": [10, 12], "suggestedWeightKg": null, "suggestedReps": 12, "suggestionReason": "NO_HISTORY",
			  "lastTime": null, "sets": []}]}"""
			.formatted(id, JsonPath.read(started.body(), "$.startedAt")), second);

		// Sets: a new set gets the next number and is not completed yet.
		HttpResponse<String> warmup = browser.post("/api/sessions/" + id + "/exercises/1/sets",
			"{\"weightKg\": 20, \"reps\": 10, \"isWarmup\": true}");
		assertThat(warmup.statusCode()).isEqualTo(201);
		String warmupId = JsonPath.read(warmup.body(), "$.id");
		STRICT.assertIsMatch("""
			{"id": "%s", "setNumber": 1, "weightKg": 20.00, "reps": 10, "isWarmup": true, "completed": false}"""
			.formatted(warmupId), warmup.body());
		String heavy1 = addSet(id, 1, "60", 8, false);
		String heavy2 = addSet(id, 1, "62.5", 6, false);
		String row1 = addSet(id, 2, "30", 12, false);
		addSet(id, 2, "30", 11, false); // never completed, so it doesn't count

		// Complete sets (the warm-up too: it still doesn't count as a working set).
		for (String setId : List.of(warmupId, heavy1, heavy2, row1)) {
			HttpResponse<String> done = browser.patch("/api/sets/" + setId, "{\"completed\": true}");
			assertThat(done.statusCode()).isEqualTo(200);
			assertThat(JsonPath.<Boolean>read(done.body(), "$.completed")).isTrue();
		}
		// Changing only the reps keeps everything else.
		HttpResponse<String> changed = browser.patch("/api/sets/" + heavy2, "{\"reps\": 7}");
		STRICT.assertIsMatch("""
			{"id": "%s", "setNumber": 3, "weightKg": 62.50, "reps": 7, "isWarmup": false, "completed": true}"""
			.formatted(heavy2), changed.body());

		// GET shows the sets under their exercises, in set-number order.
		String session = browser.get("/api/sessions/" + id).body();
		assertThat(JsonPath.<List<Integer>>read(session, "$.exercises[0].sets[*].setNumber")).containsExactly(1, 2, 3);
		assertThat(JsonPath.<List<Double>>read(session, "$.exercises[0].sets[*].weightKg"))
			.containsExactly(20.0, 60.0, 62.5);
		assertThat(JsonPath.<List<Integer>>read(session, "$.exercises[1].sets[*].reps")).containsExactly(12, 11);

		// Finish.
		HttpResponse<String> finished = browser.post("/api/sessions/" + id + "/finish", "");
		assertThat(finished.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<String>read(finished.body(), "$.status")).isEqualTo("FINISHED");
		assertThat(Instant.parse(JsonPath.read(finished.body(), "$.finishedAt")))
			.isAfterOrEqualTo(Instant.parse(JsonPath.read(finished.body(), "$.startedAt")));
		assertThat(browser.get("/api/sessions/active").statusCode()).isEqualTo(204);

		// History: one week with this workout, 2 exercises and 3 working sets.
		HttpResponse<String> history = browser.get("/api/history");
		assertThat(history.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<List<Object>>read(history.body(), "$")).hasSize(1);
		Map<String, Object> listed = JsonPath.read(history.body(), "$[0].sessions[0]");
		assertThat(listed).containsOnlyKeys("id", "name", "startedAt", "finishedAt", "exerciseCount", "workingSets")
			.containsEntry("id", id)
			.containsEntry("name", "Workout")
			.containsEntry("startedAt", JsonPath.read(finished.body(), "$.startedAt"))
			.containsEntry("finishedAt", JsonPath.read(finished.body(), "$.finishedAt"))
			.containsEntry("exerciseCount", 2)
			.containsEntry("workingSets", 3);
	}

	@Test
	void finishingWithoutAnyCompletedSetIsAllowed() throws Exception {
		String id = startSession();
		addExercise(id, "push_up");
		addSet(id, 1, "0", 15, false);

		HttpResponse<String> finished = browser.post("/api/sessions/" + id + "/finish", "");

		assertThat(finished.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<String>read(finished.body(), "$.status")).isEqualTo("FINISHED");
		assertThat(JsonPath.<Integer>read(browser.get("/api/history").body(), "$[0].sessions[0].workingSets")).isZero();
	}

	// ---- starting ----

	@Test
	void startWithoutABodyWorksToo() throws Exception {
		HttpResponse<String> started = browser.post("/api/sessions", "");

		assertThat(started.statusCode()).isEqualTo(201);
		assertThat(JsonPath.<String>read(started.body(), "$.name")).isEqualTo("Workout");
	}

	@Test
	void startingWithASplitDayUsesItsName() throws Exception {
		String settings = browser.put("/api/settings", """
			{"splitType": "PPL", "units": "KG", "timezone": "UTC", "equipment": []}""").body();
		String pullDayId = JsonPath.read(settings, "$.splitDays[1].id");

		HttpResponse<String> started = browser.post("/api/sessions", "{\"splitDayId\": \"" + pullDayId + "\"}");

		assertThat(started.statusCode()).isEqualTo(201);
		assertThat(JsonPath.<String>read(started.body(), "$.name")).isEqualTo("Pull");
	}

	@Test
	void someoneElsesOrAnUnknownSplitDayIs400() throws Exception {
		TestBrowser other = new TestBrowser(port);
		other.signUpNewUser();
		String otherSettings = other.put("/api/settings", """
			{"splitType": "FB", "units": "KG", "timezone": "UTC", "equipment": []}""").body();
		String otherDayId = JsonPath.read(otherSettings, "$.splitDays[0].id");

		for (String dayId : List.of(otherDayId, "00000000-0000-0000-0000-000000000000")) {
			HttpResponse<String> response = browser.post("/api/sessions", "{\"splitDayId\": \"" + dayId + "\"}");

			assertThat(response.statusCode()).isEqualTo(400);
			assertThat(response.body())
				.isEqualTo("{\"error\":\"Invalid input\",\"fields\":{\"splitDayId\":\"unknown split day\"}}");
		}
		assertThat(browser.get("/api/sessions/active").statusCode()).isEqualTo(204); // nothing was started
	}

	@Test
	void aSecondStartWhileOneIsActiveIs409() throws Exception {
		String first = startSession();

		HttpResponse<String> second = browser.post("/api/sessions", "{}");

		assertThat(second.statusCode()).isEqualTo(409);
		assertThat(second.body()).isEqualTo(ALREADY_ACTIVE);
		assertThat(JsonPath.<String>read(browser.get("/api/sessions/active").body(), "$.id")).isEqualTo(first);
	}

	@Test
	void startingSeveralAtTheSameMomentStillGivesOnlyOneWorkout() throws Exception {
		List<HttpResponse<String>> responses = atTheSameMoment(5, () -> browser.post("/api/sessions", "{}"));

		assertThat(responses).extracting(HttpResponse::statusCode).containsOnly(201, 409).containsOnlyOnce(201);
	}

	@Test
	void theDatabaseAllowsOnlyOneActiveWorkoutPerUser() throws Exception {
		startSession();
		String userId = JsonPath.read(browser.get("/api/me").body(), "$.id");
		String insert = "insert into workout_session (user_id, name, status) values (?::uuid, 'Extra', ?)";

		assertThatThrownBy(() -> jdbcTemplate.update(insert, userId, "ACTIVE"))
			.isInstanceOf(DataIntegrityViolationException.class);
		// Any number of finished workouts is fine.
		jdbcTemplate.update(insert, userId, "FINISHED");
		jdbcTemplate.update(insert, userId, "FINISHED");

		// Saved through JPA like the service does, the error names exactly the index the service looks for
		// (so a lost race on "start" really turns into the 409).
		WorkoutSession second = new WorkoutSession(UUID.fromString(userId), null, "Extra", Instant.now());
		assertThatThrownBy(() -> sessionRepository.saveAndFlush(second))
			.isInstanceOfSatisfying(DataIntegrityViolationException.class,
				e -> assertThat(WorkoutService.isSecondActiveSession(e)).isTrue());
	}

	@Test
	void afterFinishingANewWorkoutCanBeStarted() throws Exception {
		String first = startSession();
		browser.post("/api/sessions/" + first + "/finish", "");

		HttpResponse<String> second = browser.post("/api/sessions", "{}");

		assertThat(second.statusCode()).isEqualTo(201);
		assertThat(JsonPath.<String>read(second.body(), "$.id")).isNotEqualTo(first);
	}

	// ---- resuming ----

	@Test
	void activeIs204WithoutAWorkoutAndResumesTheWorkoutFromAnotherBrowser() throws Exception {
		assertThat(browser.get("/api/sessions/active").statusCode()).isEqualTo(204);

		String email = new TestBrowser(port).signUpNewUser(); // a fresh user, logged in on a "phone"...
		TestBrowser phone = new TestBrowser(port);
		phone.get("/api/auth/csrf");
		phone.post("/api/auth/login", loginJson(email, PASSWORD));
		HttpResponse<String> started = phone.post("/api/sessions", "{}");
		String id = JsonPath.read(started.body(), "$.id");
		phone.post("/api/sessions/" + id + "/exercises", "{\"exerciseId\": \"push_up\"}");
		phone.post("/api/sessions/" + id + "/exercises/1/sets", "{\"weightKg\": 0, \"reps\": 15, \"isWarmup\": false}");

		// ... then the same user logs in on a "laptop": the workout and its set are still there.
		TestBrowser laptop = new TestBrowser(port);
		laptop.get("/api/auth/csrf");
		laptop.post("/api/auth/login", loginJson(email, PASSWORD));
		HttpResponse<String> active = laptop.get("/api/sessions/active");

		assertThat(active.statusCode()).isEqualTo(200);
		assertThat(active.body()).isEqualTo(phone.get("/api/sessions/" + id).body());
		assertThat(JsonPath.<Integer>read(active.body(), "$.exercises[0].sets[0].reps")).isEqualTo(15);
	}

	// ---- double taps: changes to one workout run one after the other ----

	@Test
	void setsAddedAtTheSameMomentGetNumbers1To5() throws Exception {
		String id = startSession();
		addExercise(id, "cable_fly");

		List<HttpResponse<String>> responses = atTheSameMoment(5, () -> browser.post(
			"/api/sessions/" + id + "/exercises/1/sets", "{\"weightKg\": 10, \"reps\": 12}"));

		assertThat(responses).extracting(HttpResponse::statusCode).containsOnly(201);
		assertThat(responses).extracting(response -> JsonPath.<Integer>read(response.body(), "$.setNumber"))
			.containsExactlyInAnyOrder(1, 2, 3, 4, 5);
		assertThat(JsonPath.<List<Integer>>read(browser.get("/api/sessions/" + id).body(),
			"$.exercises[0].sets[*].setNumber")).containsExactly(1, 2, 3, 4, 5);
	}

	@Test
	void oneTapCompletesAtTheSameMomentGetNumbers1To6() throws Exception {
		String id = startSession();
		addExercise(id, "cable_fly");

		// A double tap on DONE: both taps are logged, one after the other, never mixed up.
		List<HttpResponse<String>> responses = atTheSameMoment(2, () -> browser.post(
			"/api/sessions/" + id + "/exercises/1/complete", "{\"weightKg\": 10, \"reps\": 12, \"sets\": 3}"));

		assertThat(responses).extracting(HttpResponse::statusCode).containsOnly(200);
		assertThat(JsonPath.<List<Integer>>read(browser.get("/api/sessions/" + id).body(),
			"$.exercises[0].sets[*].setNumber")).containsExactly(1, 2, 3, 4, 5, 6);
	}

	@Test
	void exercisesAddedAtTheSameMomentGetPositions1To5() throws Exception {
		String id = startSession();

		List<HttpResponse<String>> responses = atTheSameMoment(5, () -> browser.post(
			"/api/sessions/" + id + "/exercises", "{\"exerciseId\": \"push_up\"}"));

		assertThat(responses).extracting(HttpResponse::statusCode).containsOnly(200);
		assertThat(JsonPath.<List<Integer>>read(browser.get("/api/sessions/" + id).body(),
			"$.exercises[*].position")).containsExactly(1, 2, 3, 4, 5);
	}

	@Test
	void setsDeletedAtTheSameMomentLeaveCleanNumbers() throws Exception {
		String id = startSession();
		addExercise(id, "cable_fly");
		List<String> setIds = List.of(addSet(id, 1, "10", 12, false), addSet(id, 1, "10", 11, false),
			addSet(id, 1, "10", 10, false), addSet(id, 1, "10", 9, false), addSet(id, 1, "10", 8, false));

		// Delete sets 1, 3 and 5 at once.
		List<HttpResponse<String>> responses = deleteSetsAtTheSameMoment(setIds.get(0), setIds.get(2), setIds.get(4));

		assertThat(responses).extracting(HttpResponse::statusCode).containsOnly(204);
		String session = browser.get("/api/sessions/" + id).body();
		assertThat(JsonPath.<List<Integer>>read(session, "$.exercises[0].sets[*].setNumber")).containsExactly(1, 2);
		assertThat(JsonPath.<List<Integer>>read(session, "$.exercises[0].sets[*].reps")).containsExactly(11, 9);
	}

	// ---- discarding ----

	@Test
	void discardingTheActiveWorkoutDeletesIt() throws Exception {
		String id = startSession();
		addExercise(id, "push_up");
		String setId = addSet(id, 1, "0", 10, false);

		HttpResponse<String> discarded = browser.delete("/api/sessions/" + id);

		assertThat(discarded.statusCode()).isEqualTo(204);
		assertThat(browser.get("/api/sessions/" + id).statusCode()).isEqualTo(404);
		assertThat(browser.patch("/api/sets/" + setId, "{\"reps\": 1}").statusCode()).isEqualTo(404);
		assertThat(browser.get("/api/sessions/active").statusCode()).isEqualTo(204);
		assertThat(browser.post("/api/sessions", "{}").statusCode()).isEqualTo(201);
	}

	@Test
	void aFinishedWorkoutCantBeDiscarded() throws Exception {
		String id = startSession();
		browser.post("/api/sessions/" + id + "/finish", "");

		HttpResponse<String> discarded = browser.delete("/api/sessions/" + id);

		assertThat(discarded.statusCode()).isEqualTo(409);
		assertThat(discarded.body()).isEqualTo(FINISHED);
		assertThat(browser.get("/api/sessions/" + id).statusCode()).isEqualTo(200);
	}

	// ---- finished workouts are read-only ----

	@Test
	void everyChangeToAFinishedWorkoutIs409() throws Exception {
		String id = startSession();
		addExercise(id, "push_up");
		String setId = addSet(id, 1, "0", 10, false);
		browser.post("/api/sessions/" + id + "/finish", "");
		String before = browser.get("/api/sessions/" + id).body();

		List<HttpResponse<String>> attempts = List.of(
			browser.post("/api/sessions/" + id + "/exercises", "{\"exerciseId\": \"cable_fly\"}"),
			browser.delete("/api/sessions/" + id + "/exercises/1"),
			browser.post("/api/sessions/" + id + "/exercises/1/sets", "{\"weightKg\": 0, \"reps\": 5}"),
			browser.patch("/api/sets/" + setId, "{\"completed\": true}"),
			browser.delete("/api/sets/" + setId),
			browser.post("/api/sessions/" + id + "/finish", ""));

		for (HttpResponse<String> attempt : attempts) {
			assertThat(attempt.statusCode()).as(attempt.request().method() + " " + attempt.uri()).isEqualTo(409);
			assertThat(attempt.body()).isEqualTo(FINISHED);
		}
		assertThat(browser.get("/api/sessions/" + id).body()).isEqualTo(before);
	}

	// ---- removing and renumbering ----

	@Test
	void removingAnExerciseDeletesItsSetsAndMovesTheLaterOnesUp() throws Exception {
		String id = startSession();
		addExercise(id, "incline_barbell_press");
		addExercise(id, "single_arm_cable_row");
		addExercise(id, "cable_fly");
		addExercise(id, "push_up");
		String removedSet = addSet(id, 2, "30", 12, false);
		String flySet = addSet(id, 3, "15", 12, false);

		HttpResponse<String> response = browser.delete("/api/sessions/" + id + "/exercises/2");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<List<Integer>>read(response.body(), "$.exercises[*].position")).containsExactly(1, 2, 3);
		assertThat(JsonPath.<List<String>>read(response.body(), "$.exercises[*].exerciseId"))
			.containsExactly("incline_barbell_press", "cable_fly", "push_up");
		assertThat(JsonPath.<List<String>>read(response.body(), "$.exercises[1].sets[*].id")).containsExactly(flySet);
		assertThat(browser.get("/api/sessions/" + id).body()).isEqualTo(response.body());
		assertThat(browser.patch("/api/sets/" + removedSet, "{\"reps\": 1}").statusCode()).isEqualTo(404);

		// The next exercise goes after the last one, and sets go to the right exercise by its new position.
		String afterAdd = addExercise(id, "single_arm_cable_row");
		assertThat(JsonPath.<List<Integer>>read(afterAdd, "$.exercises[*].position")).containsExactly(1, 2, 3, 4);
		addSet(id, 2, "17.5", 10, false);
		assertThat(JsonPath.<List<Double>>read(browser.get("/api/sessions/" + id).body(),
			"$.exercises[1].sets[*].weightKg")).containsExactly(15.0, 17.5);
	}

	@Test
	void removingTheLastExerciseOrAnUnknownPositionIsHandled() throws Exception {
		String id = startSession();
		addExercise(id, "push_up");

		HttpResponse<String> unknown = browser.delete("/api/sessions/" + id + "/exercises/2");
		assertThat(unknown.statusCode()).isEqualTo(404);
		assertThat(unknown.body()).isEqualTo("{\"error\":\"No exercise at position 2 in this workout\"}");

		HttpResponse<String> removed = browser.delete("/api/sessions/" + id + "/exercises/1");
		assertThat(removed.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<List<Object>>read(removed.body(), "$.exercises")).isEmpty();
	}

	@Test
	void deletingASetRenumbersTheRemainingSets() throws Exception {
		String id = startSession();
		addExercise(id, "cable_fly");
		String set1 = addSet(id, 1, "10", 15, true);
		String set2 = addSet(id, 1, "15", 12, false);
		String set3 = addSet(id, 1, "17.5", 10, false);

		HttpResponse<String> deleted = browser.delete("/api/sets/" + set1);

		assertThat(deleted.statusCode()).isEqualTo(204);
		String session = browser.get("/api/sessions/" + id).body();
		assertThat(JsonPath.<List<String>>read(session, "$.exercises[0].sets[*].id")).containsExactly(set2, set3);
		assertThat(JsonPath.<List<Integer>>read(session, "$.exercises[0].sets[*].setNumber")).containsExactly(1, 2);

		// A new set takes the next number after the renumbering.
		HttpResponse<String> added = browser.post("/api/sessions/" + id + "/exercises/1/sets",
			"{\"weightKg\": 17.5, \"reps\": 9, \"isWarmup\": false}");
		assertThat(JsonPath.<Integer>read(added.body(), "$.setNumber")).isEqualTo(3);
	}

	@Test
	void unknownSetOrExercisePositionIs404() throws Exception {
		String id = startSession();
		String unknownSet = "/api/sets/00000000-0000-0000-0000-000000000000";

		assertThat(browser.patch(unknownSet, "{\"reps\": 1}").body()).isEqualTo(SET_NOT_FOUND);
		assertThat(browser.delete(unknownSet).statusCode()).isEqualTo(404);
		HttpResponse<String> noExercise = browser.post("/api/sessions/" + id + "/exercises/1/sets",
			"{\"weightKg\": 10, \"reps\": 5}");
		assertThat(noExercise.statusCode()).isEqualTo(404);
		assertThat(browser.get("/api/sessions/00000000-0000-0000-0000-000000000000").body())
			.isEqualTo(WORKOUT_NOT_FOUND);
	}

	// ---- validation ----

	@Test
	void setValuesOutsideTheLimitsAre400WithFields() throws Exception {
		String id = startSession();
		addExercise(id, "cable_fly");
		String path = "/api/sessions/" + id + "/exercises/1/sets";

		assertThat(fieldsOf(browser.post(path, "{\"weightKg\": -0.5, \"reps\": 101}"))).containsOnly(
			Map.entry("weightKg", "must be between 0 and 1000"), Map.entry("reps", "must be between 0 and 100"));
		assertThat(fieldsOf(browser.post(path, "{\"weightKg\": 1000.01, \"reps\": -1}"))).containsOnly(
			Map.entry("weightKg", "must be between 0 and 1000"), Map.entry("reps", "must be between 0 and 100"));
		assertThat(fieldsOf(browser.post(path, "{\"weightKg\": 12.345, \"reps\": 5}"))).containsOnly(
			Map.entry("weightKg", "must have at most 2 decimals"));
		assertThat(fieldsOf(browser.post(path, "{\"isWarmup\": true}"))).containsOnly(
			Map.entry("weightKg", "is required"), Map.entry("reps", "is required"));

		// The limits themselves are fine; isWarmup may be left out (= false).
		HttpResponse<String> highest = browser.post(path, "{\"weightKg\": 1000, \"reps\": 100}");
		assertThat(highest.statusCode()).isEqualTo(201);
		assertThat(JsonPath.<Boolean>read(highest.body(), "$.isWarmup")).isFalse();
		assertThat(browser.post(path, "{\"weightKg\": 0, \"reps\": 0}").statusCode()).isEqualTo(201);
		assertThat(browser.post(path, "{\"weightKg\": 2.25, \"reps\": 1}").statusCode()).isEqualTo(201);
		// Nothing invalid was saved.
		assertThat(JsonPath.<List<Object>>read(browser.get("/api/sessions/" + id).body(), "$.exercises[0].sets"))
			.hasSize(3);
	}

	@Test
	void setChangesAreCheckedTooAndNothingIsSavedWhenInvalid() throws Exception {
		String id = startSession();
		addExercise(id, "cable_fly");
		String setId = addSet(id, 1, "10", 12, false);

		HttpResponse<String> response = browser.patch("/api/sets/" + setId,
			"{\"weightKg\": 2000, \"reps\": 500, \"completed\": true}");

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(JsonPath.<String>read(response.body(), "$.error")).isEqualTo("Invalid input");
		assertThat(fieldsOf(response)).containsOnlyKeys("weightKg", "reps");
		String session = browser.get("/api/sessions/" + id).body();
		assertThat(JsonPath.<Boolean>read(session, "$.exercises[0].sets[0].completed")).isFalse();
		assertThat(JsonPath.<Integer>read(session, "$.exercises[0].sets[0].reps")).isEqualTo(12);
	}

	@Test
	void anIdThatIsNotAUuidIs400() throws Exception {
		HttpResponse<String> session = browser.get("/api/sessions/abc");
		HttpResponse<String> set = browser.patch("/api/sets/abc", "{\"reps\": 1}");

		assertThat(session.statusCode()).isEqualTo(400);
		assertThat(session.body()).isEqualTo("{\"error\":\"Invalid value 'abc' for parameter 'id'\"}");
		assertThat(set.statusCode()).isEqualTo(400);
		assertThat(set.body()).isEqualTo("{\"error\":\"Invalid value 'abc' for parameter 'setId'\"}");
	}

	@Test
	void anExplicitNullInAPatchKeepsTheValue() throws Exception {
		String id = startSession();
		addExercise(id, "cable_fly");
		String setId = addSet(id, 1, "10", 12, false);

		HttpResponse<String> response = browser.patch("/api/sets/" + setId, "{\"reps\": null, \"completed\": true}");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<Integer>read(response.body(), "$.reps")).isEqualTo(12);
		assertThat(JsonPath.<Boolean>read(response.body(), "$.completed")).isTrue();
		assertThat(JsonPath.<Integer>read(browser.get("/api/sessions/" + id).body(), "$.exercises[0].sets[0].reps"))
			.isEqualTo(12);
	}

	@Test
	void unknownOrMissingExerciseIdIs400() throws Exception {
		String id = startSession();

		assertThat(fieldsOf(browser.post("/api/sessions/" + id + "/exercises", "{\"exerciseId\": \"jetpack\"}")))
			.containsOnly(Map.entry("exerciseId", "unknown exercise"));
		assertThat(fieldsOf(browser.post("/api/sessions/" + id + "/exercises", "{}")))
			.containsOnly(Map.entry("exerciseId", "is required"));
	}

	// ---- other users ----

	@Test
	void anotherUserGets404ForEverythingOfMine() throws Exception {
		String id = startSession();
		addExercise(id, "cable_fly");
		String setId = addSet(id, 1, "10", 12, false);
		TestBrowser other = new TestBrowser(port);
		other.signUpNewUser();

		List<HttpResponse<String>> workoutAttempts = List.of(
			other.get("/api/sessions/" + id),
			other.post("/api/sessions/" + id + "/exercises", "{\"exerciseId\": \"push_up\"}"),
			other.delete("/api/sessions/" + id + "/exercises/1"),
			other.post("/api/sessions/" + id + "/exercises/1/sets", "{\"weightKg\": 1, \"reps\": 1}"),
			other.post("/api/sessions/" + id + "/finish", ""),
			other.delete("/api/sessions/" + id));
		for (HttpResponse<String> attempt : workoutAttempts) {
			assertThat(attempt.statusCode()).as(attempt.request().method() + " " + attempt.uri()).isEqualTo(404);
			assertThat(attempt.body()).isEqualTo(WORKOUT_NOT_FOUND);
		}
		for (HttpResponse<String> attempt : List.of(
				other.patch("/api/sets/" + setId, "{\"reps\": 1}"), other.delete("/api/sets/" + setId))) {
			assertThat(attempt.statusCode()).isEqualTo(404);
			assertThat(attempt.body()).isEqualTo(SET_NOT_FOUND);
		}

		// The other user has no active workout of their own, and mine is untouched.
		assertThat(other.get("/api/sessions/active").statusCode()).isEqualTo(204);
		String mine = browser.get("/api/sessions/" + id).body();
		assertThat(JsonPath.<String>read(mine, "$.status")).isEqualTo("ACTIVE");
		assertThat(JsonPath.<Integer>read(mine, "$.exercises[0].sets[0].reps")).isEqualTo(12);

		// A finished workout of mine is just as invisible: still 404, not 409.
		browser.post("/api/sessions/" + id + "/finish", "");
		assertThat(other.delete("/api/sessions/" + id).statusCode()).isEqualTo(404);
		assertThat(other.get("/api/history").body()).isEqualTo("[]");
	}

	@Test
	void everyWorkoutEndpointNeedsALogin() throws Exception {
		TestBrowser anonymous = new TestBrowser(port);
		anonymous.get("/api/auth/csrf"); // a valid CSRF token, but no login
		String someId = "00000000-0000-0000-0000-000000000000";

		List<HttpResponse<String>> attempts = List.of(
			anonymous.get("/api/sessions/active"),
			anonymous.post("/api/sessions", "{}"),
			anonymous.get("/api/sessions/" + someId),
			anonymous.post("/api/sessions/" + someId + "/exercises", "{\"exerciseId\": \"push_up\"}"),
			anonymous.delete("/api/sessions/" + someId + "/exercises/1"),
			anonymous.post("/api/sessions/" + someId + "/exercises/1/sets", "{\"weightKg\": 1, \"reps\": 1}"),
			anonymous.patch("/api/sets/" + someId, "{\"reps\": 1}"),
			anonymous.delete("/api/sets/" + someId),
			anonymous.post("/api/sessions/" + someId + "/finish", ""),
			anonymous.delete("/api/sessions/" + someId),
			anonymous.get("/api/history"));
		for (HttpResponse<String> attempt : attempts) {
			assertThat(attempt.statusCode()).as(attempt.request().method() + " " + attempt.uri()).isEqualTo(401);
			assertThat(attempt.body()).isEqualTo("{\"error\":\"Login required\"}");
		}
	}

	@Test
	void changesWithoutACsrfTokenAre403() throws Exception {
		HttpResponse<String> response = browser.postWithoutCsrfToken("/api/sessions", "{}");

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(browser.get("/api/sessions/active").statusCode()).isEqualTo(204);
	}

	// ---- helpers ----

	private String startSession() throws Exception {
		HttpResponse<String> response = browser.post("/api/sessions", "{}");
		assertThat(response.statusCode()).isEqualTo(201);
		return JsonPath.read(response.body(), "$.id");
	}

	/** Adds an exercise and returns the whole session JSON. */
	private String addExercise(String sessionId, String exerciseId) throws Exception {
		HttpResponse<String> response = browser.post("/api/sessions/" + sessionId + "/exercises",
			"{\"exerciseId\": \"" + exerciseId + "\"}");
		assertThat(response.statusCode()).isEqualTo(200);
		return response.body();
	}

	/** Adds a set and returns its id. */
	private String addSet(String sessionId, int position, String weightKg, int reps, boolean warmup) throws Exception {
		HttpResponse<String> response = browser.post("/api/sessions/" + sessionId + "/exercises/" + position + "/sets",
			"{\"weightKg\": %s, \"reps\": %d, \"isWarmup\": %b}".formatted(weightKg, reps, warmup));
		assertThat(response.statusCode()).isEqualTo(201);
		return JsonPath.read(response.body(), "$.id");
	}

	/** Sends the same request from {@code count} threads at once and returns all the answers. */
	private static List<HttpResponse<String>> atTheSameMoment(int count, Callable<HttpResponse<String>> request)
			throws InterruptedException {
		try (ExecutorService pool = Executors.newFixedThreadPool(count)) {
			List<Future<HttpResponse<String>>> results = pool.invokeAll(Collections.nCopies(count, request));
			return results.stream().map(WorkoutIntegrationTest::await).toList();
		}
	}

	/** Deletes these sets, each from its own thread, all at once. */
	private List<HttpResponse<String>> deleteSetsAtTheSameMoment(String... setIds) throws InterruptedException {
		try (ExecutorService pool = Executors.newFixedThreadPool(setIds.length)) {
			List<Callable<HttpResponse<String>>> deletes = Arrays.stream(setIds)
				.<Callable<HttpResponse<String>>>map(setId -> () -> browser.delete("/api/sets/" + setId))
				.toList();
			return pool.invokeAll(deletes).stream().map(WorkoutIntegrationTest::await).toList();
		}
	}

	private static <T> T await(Future<T> result) {
		try {
			return result.get();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static Map<String, String> fieldsOf(HttpResponse<String> response) {
		assertThat(response.statusCode()).isEqualTo(400);
		return JsonPath.read(response.body(), "$.fields");
	}

}
