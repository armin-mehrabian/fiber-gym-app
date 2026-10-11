package app.fiber.recommend;

import static app.fiber.TestWorkouts.addExercise;
import static app.fiber.TestWorkouts.chooseSplit;
import static app.fiber.TestWorkouts.finish;
import static app.fiber.TestWorkouts.finishAt;
import static app.fiber.TestWorkouts.logWorkingSets;
import static app.fiber.TestWorkouts.onlyTheseTargets;
import static app.fiber.TestWorkouts.splitDayId;
import static app.fiber.TestWorkouts.startWorkout;
import static org.assertj.core.api.Assertions.assertThat;

import app.fiber.TestBrowser;
import app.fiber.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
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
 * GET /api/recommendation/next and /alternatives end to end, with the test library:
 * - incline_barbell_press (barbell + bench, horizontal_push): primary chest_upper, chest_mid; secondary delt_front; 6-10
 * - push_up (bodyweight, horizontal_push): primary chest_mid; secondary delt_front; 8-20
 * - cable_fly (cables, fly): primary chest_mid; 10-15
 * - single_arm_cable_row (cables, horizontal_pull): primary lats_lower; secondary lats_upper, biceps_long; 10-12
 * - barbell_floor_press: retired, so never recommended
 * There are no leg exercises, so a Legs day has nothing to recommend.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RecommendIntegrationTest {

	private static final JsonComparator STRICT = JsonAssert.comparator(JsonCompareMode.STRICT);
	private static final String[] ALL_EQUIPMENT = { "cables", "barbell", "bench" };

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

	// ---- next ----

	@Test
	void aNewUserGetsPushWithTheExercisesThatFillTheMostGaps() throws Exception {
		String pushId = splitDayId(chooseSplit(browser, "PPL", ALL_EQUIPMENT), "Push");

		HttpResponse<String> response = browser.get("/api/recommendation/next");

		// Default targets and an empty week: incline (14) first, then push-up (4.25); after that the fly
		// would only hit chest_mid, which is already full.
		assertThat(response.statusCode()).isEqualTo(200);
		STRICT.assertIsMatch("""
			{"splitDayId": "%s", "name": "Push",
			 "exercises": [
			  {"exerciseId": "incline_barbell_press", "name": "Incline barbell press",
			   "primary": ["chest_upper", "chest_mid"], "secondary": ["delt_front"], "sets": 3, "repRange": [6, 10],
			   "suggestedWeightKg": null, "suggestedReps": 10, "suggestionReason": "NO_HISTORY"},
			  {"exerciseId": "push_up", "name": "Push-up", "primary": ["chest_mid"], "secondary": ["delt_front"],
			   "sets": 3, "repRange": [8, 20], "suggestedWeightKg": null, "suggestedReps": 20,
			   "suggestionReason": "NO_HISTORY"}],
			 "planned": {"chest_mid": 1.0, "chest_upper": 1.0, "delt_front": 0.5}}""".formatted(pushId),
			response.body());
	}

	@Test
	void theDayFollowsTheWorkoutsDoneAndWrapsAround() throws Exception {
		String settings = chooseSplit(browser, "PPL", ALL_EQUIPMENT);

		assertThat(nextDayName()).isEqualTo("Push");
		finishedWorkoutOn(splitDayId(settings, "Push"), 30);
		assertThat(nextDayName()).isEqualTo("Pull");
		assertThat(JsonPath.<List<String>>read(next(), "$.exercises[*].exerciseId"))
			.containsExactly("single_arm_cable_row");
		finishedWorkoutOn(splitDayId(settings, "Pull"), 20);
		assertThat(nextDayName()).isEqualTo("Legs");
		finishedWorkoutOn(splitDayId(settings, "Legs"), 10);
		assertThat(nextDayName()).isEqualTo("Push");
	}

	@Test
	void aLastWorkoutWithoutASplitDayStartsAgainAtTheFirstDay() throws Exception {
		String settings = chooseSplit(browser, "PPL", ALL_EQUIPMENT);
		finishedWorkoutOn(splitDayId(settings, "Push"), 30);
		finishedWorkoutOn(null, 10);

		assertThat(nextDayName()).isEqualTo("Push");
	}

	@Test
	void aDayWithoutExercisesForItIsEmpty() throws Exception {
		String settings = chooseSplit(browser, "PPL", ALL_EQUIPMENT);
		finishedWorkoutOn(splitDayId(settings, "Pull"), 10);

		String json = next();

		assertThat(JsonPath.<String>read(json, "$.name")).isEqualTo("Legs");
		assertThat(JsonPath.<List<Object>>read(json, "$.exercises")).isEmpty();
		assertThat(JsonPath.<Map<String, Object>>read(json, "$.planned")).isEmpty();
	}

	@Test
	void aCoveredWeekRecommendsNothing() throws Exception {
		chooseSplit(browser, "PPL", ALL_EQUIPMENT);
		onlyTheseTargets(browser, Map.of("lats_lower", 6)); // nothing to do on Push

		String json = next();

		assertThat(JsonPath.<String>read(json, "$.name")).isEqualTo("Push");
		assertThat(JsonPath.<List<Object>>read(json, "$.exercises")).isEmpty();
	}

	@Test
	void thisWeeksSetsShrinkTheGaps() throws Exception {
		// Full body, targets only on chest_mid (6) and lats_lower (6). 2 sets of push-ups today
		// (not enough to need recovery) leave chest_mid a gap of 4.
		chooseSplit(browser, "FB", ALL_EQUIPMENT);
		onlyTheseTargets(browser, Map.of("chest_mid", 6, "lats_lower", 6));
		String id = startWorkout(browser);
		addExercise(browser, id, "push_up");
		logWorkingSets(browser, id, 1, "0", 15, 15);
		finish(browser, id);

		// Row 6 beats every chest exercise (4). Then the three chest exercises tie at 4 and the lowest id
		// (cable_fly) wins. That leaves chest_mid 1: incline 1, push-up 1 x 0.7 (same main region as the fly).
		// Then chest_mid is full and the push-up scores 0.
		assertThat(JsonPath.<List<String>>read(next(), "$.exercises[*].exerciseId"))
			.containsExactly("single_arm_cable_row", "cable_fly", "incline_barbell_press");
	}

	@Test
	void recoveringRegionsAreNotTargeted() throws Exception {
		chooseSplit(browser, "FB", ALL_EQUIPMENT);
		String id = startWorkout(browser);
		addExercise(browser, id, "incline_barbell_press");
		logWorkingSets(browser, id, 1, "60", 8, 8, 8);
		finish(browser, id);

		// chest_upper and chest_mid got 3 sets just now: every chest exercise is left out.
		String json = next();

		assertThat(JsonPath.<List<String>>read(json, "$.exercises[*].exerciseId"))
			.containsExactly("single_arm_cable_row");
		assertThat(JsonPath.<Map<String, Object>>read(json, "$.planned")).containsOnlyKeys("lats_lower",
			"lats_upper", "biceps_long");
	}

	@Test
	void onlyExercisesForTheUsersEquipmentWithTheirSuggestions() throws Exception {
		chooseSplit(browser, "PPL"); // no equipment: bodyweight only
		String id = startWorkout(browser);
		addExercise(browser, id, "push_up");
		logWorkingSets(browser, id, 1, "0", 15, 12);
		Instant earlier = Instant.now().minus(Duration.ofHours(5));
		finishAt(browser, jdbcTemplate, id, earlier, earlier.plus(Duration.ofHours(1)));

		String json = next();

		assertThat(JsonPath.<List<String>>read(json, "$.exercises[*].exerciseId")).containsExactly("push_up");
		assertThat(JsonPath.<Double>read(json, "$.exercises[0].suggestedWeightKg")).isZero();
		assertThat(JsonPath.<Integer>read(json, "$.exercises[0].suggestedReps")).isEqualTo(16);
		assertThat(JsonPath.<String>read(json, "$.exercises[0].suggestionReason")).isEqualTo("MORE_REPS");
	}

	@Test
	void anotherUsersWorkoutsDontMoveMyRotation() throws Exception {
		chooseSplit(browser, "PPL", ALL_EQUIPMENT);
		TestBrowser other = new TestBrowser(port);
		other.signUpNewUser();
		String otherPush = splitDayId(chooseSplit(other, "PPL", ALL_EQUIPMENT), "Push");
		String id = startWorkout(other, "{\"splitDayId\": \"" + otherPush + "\"}");
		addExercise(other, id, "incline_barbell_press");
		logWorkingSets(other, id, 1, "60", 8, 8, 8);
		finish(other, id);

		assertThat(nextDayName()).isEqualTo("Push");
		assertThat(JsonPath.<List<String>>read(next(), "$.exercises[*].exerciseId")).first()
			.isEqualTo("incline_barbell_press");
		assertThat(JsonPath.<String>read(other.get("/api/recommendation/next").body(), "$.name")).isEqualTo("Pull");
	}

	@Test
	void withoutASplitItIs409() throws Exception {
		HttpResponse<String> response = browser.get("/api/recommendation/next");

		assertThat(response.statusCode()).isEqualTo(409);
		assertThat(response.body()).isEqualTo("{\"error\":\"Choose a split in settings first\"}");
	}

	// ---- alternatives ----

	@Test
	void alternativesAreTheMostSimilarExercisesForThatDay() throws Exception {
		String pushId = splitDayId(chooseSplit(browser, "PPL", ALL_EQUIPMENT), "Push");

		HttpResponse<String> response = browser.get(
			"/api/recommendation/alternatives?exerciseId=incline_barbell_press&splitDayId=" + pushId);

		// The retired floor press would be as similar as the push-up, but is never offered.
		assertThat(response.statusCode()).isEqualTo(200);
		STRICT.assertIsMatch("""
			[{"exerciseId": "push_up", "name": "Push-up", "primary": ["chest_mid"], "secondary": ["delt_front"],
			  "similarity": 0.6},
			 {"exerciseId": "cable_fly", "name": "Cable fly", "primary": ["chest_mid"], "secondary": [],
			  "similarity": 0.4}]""", response.body());
	}

	@Test
	void alternativesMustFitTheDayButWithoutADayEveryRegionCounts() throws Exception {
		String pullId = splitDayId(chooseSplit(browser, "PPL", ALL_EQUIPMENT), "Pull");

		String onPull = browser.get(
			"/api/recommendation/alternatives?exerciseId=incline_barbell_press&splitDayId=" + pullId).body();
		String withoutDay = browser.get("/api/recommendation/alternatives?exerciseId=incline_barbell_press").body();
		String emptyDay = browser.get(
			"/api/recommendation/alternatives?exerciseId=incline_barbell_press&splitDayId=").body();

		assertThat(onPull).isEqualTo("[]");
		assertThat(JsonPath.<List<String>>read(withoutDay, "$[*].exerciseId")).containsExactly("push_up", "cable_fly");
		assertThat(emptyDay).isEqualTo(withoutDay);
	}

	@Test
	void alternativesOnlyUseTheUsersEquipmentAndSkipRecoveringRegions() throws Exception {
		chooseSplit(browser, "FB");
		assertThat(JsonPath.<List<String>>read(
			browser.get("/api/recommendation/alternatives?exerciseId=incline_barbell_press").body(), "$[*].exerciseId"))
			.containsExactly("push_up");

		String id = startWorkout(browser);
		addExercise(browser, id, "push_up");
		logWorkingSets(browser, id, 1, "0", 10, 10, 10);
		finish(browser, id);

		// chest_mid is recovering now, so the push-up is no longer offered.
		assertThat(browser.get("/api/recommendation/alternatives?exerciseId=incline_barbell_press").body())
			.isEqualTo("[]");
	}

	@Test
	void badAlternativeRequestsAre400() throws Exception {
		chooseSplit(browser, "PPL", ALL_EQUIPMENT);
		TestBrowser other = new TestBrowser(port);
		other.signUpNewUser();
		String othersDay = splitDayId(chooseSplit(other, "PPL"), "Push");

		for (String query : List.of("", "?exerciseId=", "?exerciseId=%20")) {
			assertBadRequest(browser.get("/api/recommendation/alternatives" + query),
				"{\"error\":\"Invalid input\",\"fields\":{\"exerciseId\":\"is required\"}}");
		}
		assertBadRequest(browser.get("/api/recommendation/alternatives?exerciseId=nope"),
			"{\"error\":\"Invalid input\",\"fields\":{\"exerciseId\":\"unknown exercise\"}}");
		assertBadRequest(browser.get("/api/recommendation/alternatives?exerciseId=push_up&splitDayId=" + othersDay),
			"{\"error\":\"Invalid input\",\"fields\":{\"splitDayId\":\"unknown split day\"}}");
		HttpResponse<String> notAUuid = browser.get("/api/recommendation/alternatives?exerciseId=push_up&splitDayId=x");
		assertThat(notAUuid.statusCode()).isEqualTo(400);
		assertThat(JsonPath.<String>read(notAUuid.body(), "$.error")).isNotBlank();
	}

	@Test
	void bothEndpointsNeedALogin() throws Exception {
		TestBrowser anonymous = new TestBrowser(port);

		for (String path : List.of("/api/recommendation/next", "/api/recommendation/alternatives?exerciseId=push_up")) {
			HttpResponse<String> response = anonymous.get(path);
			assertThat(response.statusCode()).as(path).isEqualTo(401);
			assertThat(response.body()).isEqualTo("{\"error\":\"Login required\"}");
		}
	}

	// ---- helpers ----

	private String next() throws Exception {
		HttpResponse<String> response = browser.get("/api/recommendation/next");
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		return response.body();
	}

	private String nextDayName() throws Exception {
		return JsonPath.read(next(), "$.name");
	}

	/** A finished workout without sets on that split day (or none), that finished {@code minutesAgo} ago. */
	private void finishedWorkoutOn(String splitDayId, int minutesAgo) throws Exception {
		String body = splitDayId == null ? "{}" : "{\"splitDayId\": \"" + splitDayId + "\"}";
		String id = startWorkout(browser, body);
		Instant end = Instant.now().minus(Duration.ofMinutes(minutesAgo));
		finishAt(browser, jdbcTemplate, id, end.minus(Duration.ofMinutes(5)), end);
	}

	private static void assertBadRequest(HttpResponse<String> response, String body) {
		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.body()).isEqualTo(body);
	}

}
