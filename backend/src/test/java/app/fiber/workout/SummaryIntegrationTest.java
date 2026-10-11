package app.fiber.workout;

import static app.fiber.TestWorkouts.addExercise;
import static app.fiber.TestWorkouts.finish;
import static app.fiber.TestWorkouts.finishAt;
import static app.fiber.TestWorkouts.logSet;
import static app.fiber.TestWorkouts.logWorkingSets;
import static app.fiber.TestWorkouts.onlyTheseTargets;
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
 * GET /api/sessions/{id}/summary end to end. Workouts are logged through the API and then moved
 * into last week (Berlin time) with SQL, so the week numbers don't depend on the day the test runs.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SummaryIntegrationTest {

	private static final ZoneId BERLIN = ZoneId.of(TestBrowser.TIMEZONE);
	private static final JsonComparator STRICT = JsonAssert.comparator(JsonCompareMode.STRICT);

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private TestBrowser browser;
	private LocalDate lastMonday;

	@BeforeEach
	void signUp() throws Exception {
		browser = new TestBrowser(port);
		browser.signUpNewUser();
		lastMonday = WeekMath.weekStart(Instant.now(), BERLIN).minusWeeks(1);
	}

	@Test
	void aWorkoutSummaryWithWeekChangeRegionsAndPersonalBests() throws Exception {
		onlyTheseTargets(browser, Map.of("chest_upper", 6, "chest_mid", 6));
		// Tuesday: incline 60 x 10 three times, push-ups 0 x 15.
		String tuesday = startWorkout(browser);
		addExercise(browser, tuesday, "incline_barbell_press");
		logWorkingSets(browser, tuesday, 1, "60", 10, 10, 10);
		addExercise(browser, tuesday, "push_up");
		logWorkingSets(browser, tuesday, 2, "0", 15);
		Instant tuesdayStart = at(lastMonday.plusDays(1), 10, 0);
		finishAt(browser, jdbcTemplate, tuesday, tuesdayStart, tuesdayStart.plus(Duration.ofHours(1)));

		// Thursday, 45 min 40 s long.
		String thursday = startWorkout(browser);
		addExercise(browser, thursday, "incline_barbell_press");
		logSet(browser, thursday, 1, "100", 3, true, true); // warm-up: would beat everything, but never counts
		logWorkingSets(browser, thursday, 1, "62.5", 10); // 62.5 x 10 beats 60 x 10: a personal best
		logWorkingSets(browser, thursday, 1, "60", 8);
		addExercise(browser, thursday, "push_up");
		logWorkingSets(browser, thursday, 2, "0", 16); // bodyweight: 16 reps beats 15
		addExercise(browser, thursday, "single_arm_cable_row");
		logWorkingSets(browser, thursday, 3, "30", 12); // first time ever: not a personal best
		addExercise(browser, thursday, "cable_fly");
		logSet(browser, thursday, 4, "10", 12, true, true); // only a warm-up: no region hit
		Instant thursdayStart = at(lastMonday.plusDays(3), 18, 0);
		finishAt(browser, jdbcTemplate, thursday, thursdayStart, thursdayStart.plusSeconds(45 * 60 + 40));

		HttpResponse<String> response = browser.get("/api/sessions/" + thursday + "/summary");

		// Week before Thursday: chest_upper 3, chest_mid 3 + 1 = 4 -> (0.5 + 0.667) / 2 = 58%.
		// With it: chest_upper 5, chest_mid 4 + 2 + 1 = 7 (capped at 1) -> (0.833 + 1) / 2 = 92%.
		assertThat(response.statusCode()).isEqualTo(200);
		STRICT.assertIsMatch("""
			{"sessionId": "%s", "name": "Workout", "durationMinutes": 46, "workingSets": 4,
			 "regionsHit": {"biceps_long": 0.5, "chest_mid": 1.0, "chest_upper": 1.0, "delt_front": 0.5,
			  "lats_lower": 1.0, "lats_upper": 0.5},
			 "weekPercentBefore": 58, "weekPercentAfter": 92,
			 "personalBests": [
			  {"exerciseId": "incline_barbell_press", "name": "Incline barbell press", "weightKg": 62.50, "reps": 10},
			  {"exerciseId": "push_up", "name": "Push-up", "weightKg": 0.00, "reps": 16}]}""".formatted(thursday),
			response.body());

		// Tuesday's summary: "before" is the week without Tuesday, so Thursday's sets still count there:
		// chest_upper 2, chest_mid 3 -> (0.333 + 0.5) / 2 = 42%. With Tuesday it is the full week, 92%.
		// Everything on Tuesday was a first attempt, and the later Thursday doesn't matter for its bests.
		String tuesdaySummary = browser.get("/api/sessions/" + tuesday + "/summary").body();
		assertThat(JsonPath.<Integer>read(tuesdaySummary, "$.weekPercentBefore")).isEqualTo(42);
		assertThat(JsonPath.<Integer>read(tuesdaySummary, "$.weekPercentAfter")).isEqualTo(92);
		assertThat(JsonPath.<List<Object>>read(tuesdaySummary, "$.personalBests")).isEmpty();
	}

	@Test
	void matchingTheBestIsNotAPersonalBest() throws Exception {
		// 90 x 10 and 100 x 6 have exactly the same estimated one-rep max (120).
		finishedLastWeek(1, "incline_barbell_press", "90", 10);
		String id = finishedLastWeek(2, "incline_barbell_press", "100", 6);

		assertThat(JsonPath.<List<Object>>read(browser.get("/api/sessions/" + id + "/summary").body(),
			"$.personalBests")).isEmpty();
	}

	@Test
	void anotherUsersBestsDontCount() throws Exception {
		TestBrowser other = new TestBrowser(port);
		other.signUpNewUser();
		String theirs = startWorkout(other);
		addExercise(other, theirs, "push_up");
		logWorkingSets(other, theirs, 1, "0", 5);
		Instant start = at(lastMonday, 9, 0);
		finishAt(other, jdbcTemplate, theirs, start, start.plus(Duration.ofHours(1)));

		String mine = finishedLastWeek(2, "push_up", "0", 10);

		// My first push-ups ever: not a personal best, even though someone else did fewer before.
		assertThat(JsonPath.<List<Object>>read(browser.get("/api/sessions/" + mine + "/summary").body(),
			"$.personalBests")).isEmpty();
	}

	@Test
	void anEmptyFinishedWorkoutHasAnEmptySummary() throws Exception {
		String id = startWorkout(browser);
		finish(browser, id);

		String json = browser.get("/api/sessions/" + id + "/summary").body();

		assertThat(JsonPath.<Integer>read(json, "$.workingSets")).isZero();
		assertThat(JsonPath.<Map<String, Object>>read(json, "$.regionsHit")).isEmpty();
		assertThat(JsonPath.<Integer>read(json, "$.weekPercentBefore"))
			.isEqualTo(JsonPath.<Integer>read(json, "$.weekPercentAfter"));
		assertThat(JsonPath.<List<Object>>read(json, "$.personalBests")).isEmpty();
		assertThat(JsonPath.<Integer>read(json, "$.durationMinutes")).isZero();
	}

	@Test
	void aWorkoutInProgressHasNoSummaryYet() throws Exception {
		String id = startWorkout(browser);

		HttpResponse<String> response = browser.get("/api/sessions/" + id + "/summary");

		assertThat(response.statusCode()).isEqualTo(409);
		assertThat(response.body()).isEqualTo("{\"error\":\"Finish the workout first\"}");
	}

	@Test
	void someoneElsesOrAnUnknownWorkoutIs404AndALoginIsNeeded() throws Exception {
		String id = startWorkout(browser);
		finish(browser, id);
		TestBrowser other = new TestBrowser(port);
		other.signUpNewUser();

		for (String sessionId : List.of(id, "00000000-0000-0000-0000-000000000000")) {
			HttpResponse<String> response = other.get("/api/sessions/" + sessionId + "/summary");
			assertThat(response.statusCode()).isEqualTo(404);
			assertThat(response.body()).isEqualTo("{\"error\":\"Workout not found\"}");
		}
		HttpResponse<String> anonymous = new TestBrowser(port).get("/api/sessions/" + id + "/summary");
		assertThat(anonymous.statusCode()).isEqualTo(401);
	}

	// ---- helpers ----

	/** A workout last week on Monday + {@code day} at 09:00 Berlin, one exercise with these working sets. */
	private String finishedLastWeek(int day, String exerciseId, String weightKg, int... reps) throws Exception {
		String id = startWorkout(browser);
		addExercise(browser, id, exerciseId);
		logWorkingSets(browser, id, 1, weightKg, reps);
		Instant start = at(lastMonday.plusDays(day), 9, 0);
		finishAt(browser, jdbcTemplate, id, start, start.plus(Duration.ofHours(1)));
		return id;
	}

	private static Instant at(LocalDate date, int hour, int minute) {
		return date.atTime(hour, minute).atZone(BERLIN).toInstant();
	}

}
