package app.fiber.catalog;

import static app.fiber.TestWorkouts.addExercise;
import static app.fiber.TestWorkouts.finishAt;
import static app.fiber.TestWorkouts.logSet;
import static app.fiber.TestWorkouts.logWorkingSets;
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

/** The user's best set, last time and history on GET /api/exercises/{id} and /api/exercises/{id}/history. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ExerciseHistoryIntegrationTest {

	private static final ZoneId BERLIN = ZoneId.of(TestBrowser.TIMEZONE);
	private static final JsonComparator STRICT = JsonAssert.comparator(JsonCompareMode.STRICT);
	private static final String INCLINE = "/api/exercises/incline_barbell_press";

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

	@Test
	void bestLastAndHistoryComeFromTheUsersFinishedWorkouts() throws Exception {
		// 3 days ago: a warm-up (never the best) and 60 x 10 twice.
		Instant older = Instant.now().minus(Duration.ofDays(3));
		String first = startWorkout(browser);
		addExercise(browser, first, "incline_barbell_press");
		logSet(browser, first, 1, "100", 2, true, true);
		logWorkingSets(browser, first, 1, "60", 10, 10);
		finishAt(browser, jdbcTemplate, first, older, older.plus(Duration.ofHours(1)));
		// Yesterday: 62.5 x 8 (a bit less than 60 x 10) and an unticked set.
		Instant newer = Instant.now().minus(Duration.ofDays(1));
		String second = startWorkout(browser);
		addExercise(browser, second, "incline_barbell_press");
		logWorkingSets(browser, second, 1, "62.5", 8);
		logSet(browser, second, 1, "62.5", 6, false, false);
		finishAt(browser, jdbcTemplate, second, newer, newer.plus(Duration.ofHours(1)));
		// A workout where it was added but nothing was ticked, and one still in progress: neither shows.
		String untouched = startWorkout(browser);
		addExercise(browser, untouched, "incline_barbell_press");
		logSet(browser, untouched, 1, "70", 10, false, false);
		finishAt(browser, jdbcTemplate, untouched, newer.plusSeconds(7200), newer.plusSeconds(9000));
		String active = startWorkout(browser);
		addExercise(browser, active, "incline_barbell_press");
		logWorkingSets(browser, active, 1, "200", 10);

		String olderDate = LocalDate.ofInstant(older, BERLIN).toString();
		String newerDate = LocalDate.ofInstant(newer, BERLIN).toString();
		String detail = browser.get(INCLINE).body();
		STRICT.assertIsMatch("""
			{"weightKg": 60.00, "reps": 10, "date": "%s"}""".formatted(olderDate),
			JsonPath.parse((Object) JsonPath.read(detail, "$.best")).jsonString());
		STRICT.assertIsMatch("""
			{"date": "%s", "sets": [{"weightKg": 62.50, "reps": 8}]}""".formatted(newerDate),
			JsonPath.parse((Object) JsonPath.read(detail, "$.last")).jsonString());

		HttpResponse<String> history = browser.get(INCLINE + "/history");
		assertThat(history.statusCode()).isEqualTo(200);
		STRICT.assertIsMatch("""
			[{"sessionId": "%s", "date": "%s", "sets": [{"weightKg": 62.50, "reps": 8, "isWarmup": false}]},
			 {"sessionId": "%s", "date": "%s", "sets": [{"weightKg": 100.00, "reps": 2, "isWarmup": true},
			  {"weightKg": 60.00, "reps": 10, "isWarmup": false}, {"weightKg": 60.00, "reps": 10, "isWarmup": false}]}]"""
			.formatted(second, newerDate, first, olderDate), history.body());
	}

	@Test
	void visitorsAndOtherUsersSeeNoBestOrLast() throws Exception {
		String id = startWorkout(browser);
		addExercise(browser, id, "incline_barbell_press");
		logWorkingSets(browser, id, 1, "60", 10);
		Instant start = Instant.now().minus(Duration.ofDays(1));
		finishAt(browser, jdbcTemplate, id, start, start.plus(Duration.ofHours(1)));
		TestBrowser other = new TestBrowser(port);
		other.signUpNewUser();

		for (TestBrowser someone : List.of(new TestBrowser(port), other)) {
			HttpResponse<String> response = someone.get(INCLINE);
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(JsonPath.<Object>read(response.body(), "$.best")).isNull();
			assertThat(JsonPath.<Object>read(response.body(), "$.last")).isNull();
		}
		assertThat(other.get(INCLINE + "/history").body()).isEqualTo("[]");
	}

	@Test
	void datesAreDaysInTheUsersTimezone() throws Exception {
		TestBrowser vancouver = new TestBrowser(port);
		vancouver.signUpNewUser("America/Vancouver");
		// 03:00 UTC is still the evening before in Vancouver.
		LocalDate utcDay = LocalDate.now(ZoneId.of("UTC")).minusDays(2);
		Instant start = utcDay.atTime(3, 0).atZone(ZoneId.of("UTC")).toInstant();
		String id = startWorkout(vancouver);
		addExercise(vancouver, id, "push_up");
		logWorkingSets(vancouver, id, 1, "0", 12);
		finishAt(vancouver, jdbcTemplate, id, start, start.plus(Duration.ofHours(1)));

		String expected = utcDay.minusDays(1).toString();
		assertThat(JsonPath.<String>read(vancouver.get("/api/exercises/push_up").body(), "$.best.date"))
			.isEqualTo(expected);
		assertThat(JsonPath.<String>read(vancouver.get("/api/exercises/push_up").body(), "$.last.date"))
			.isEqualTo(expected);
		assertThat(JsonPath.<String>read(vancouver.get("/api/exercises/push_up/history").body(), "$[0].date"))
			.isEqualTo(expected);
	}

	@Test
	void historyShowsAtMostTheNewest20Workouts() throws Exception {
		Instant first = Instant.now().minus(Duration.ofDays(30));
		for (int i = 0; i < 21; i++) {
			String id = startWorkout(browser);
			addExercise(browser, id, "cable_fly");
			logWorkingSets(browser, id, 1, "10", i + 1); // reps tell the workouts apart
			Instant start = first.plus(Duration.ofHours(i));
			finishAt(browser, jdbcTemplate, id, start, start.plus(Duration.ofMinutes(30)));
		}

		List<Integer> reps = JsonPath.read(browser.get("/api/exercises/cable_fly/history").body(), "$[*].sets[0].reps");

		assertThat(reps).hasSize(20).first().isEqualTo(21);
		assertThat(reps).last().isEqualTo(2);
	}

	@Test
	void historyNeedsALoginAndAKnownExercise() throws Exception {
		HttpResponse<String> anonymous = new TestBrowser(port).get(INCLINE + "/history");
		HttpResponse<String> unknown = browser.get("/api/exercises/nope/history");

		assertThat(anonymous.statusCode()).isEqualTo(401);
		assertThat(anonymous.body()).isEqualTo("{\"error\":\"Login required\"}");
		assertThat(unknown.statusCode()).isEqualTo(404);
		assertThat(unknown.body()).isEqualTo("{\"error\":\"Exercise not found: nope\"}");
	}

}
