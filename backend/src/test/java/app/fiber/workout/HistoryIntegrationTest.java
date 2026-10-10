package app.fiber.workout;

import static org.assertj.core.api.Assertions.assertThat;

import app.fiber.TestBrowser;
import app.fiber.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * GET /api/history end to end. Workouts are created through the API, then their start and finish
 * times are moved to exact moments with SQL, so the week boundaries can be tested precisely.
 * The moments are relative to the current week, so the tests work on any day they run.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class HistoryIntegrationTest {

	private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
	private static final ZoneId AUCKLAND = ZoneId.of("Pacific/Auckland");
	private static final ZoneId VANCOUVER = ZoneId.of("America/Vancouver");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void newUserHasAnEmptyHistory() throws Exception {
		TestBrowser browser = signedUpIn(BERLIN);

		HttpResponse<String> response = browser.get("/api/history");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("[]");
	}

	@Test
	void mondayMidnightStartsANewWeekAndSundayNightBelongsToTheOldOne() throws Exception {
		TestBrowser browser = signedUpIn(BERLIN);
		LocalDate lastMonday = thisMonday(BERLIN).minusWeeks(1);
		Instant mondayMidnight = lastMonday.atStartOfDay(BERLIN).toInstant();
		String sundayNight = finishedWorkoutAt(browser, mondayMidnight.minusSeconds(1)); // Sunday 23:59:59
		String monday = finishedWorkoutAt(browser, mondayMidnight); // Monday 00:00:00

		String history = browser.get("/api/history").body();

		assertThat(weekStarts(history)).containsExactly(lastMonday.toString(), lastMonday.minusWeeks(1).toString());
		assertThat(JsonPath.<List<String>>read(history, "$[0].sessions[*].id")).containsExactly(monday);
		assertThat(JsonPath.<List<String>>read(history, "$[1].sessions[*].id")).containsExactly(sundayNight);
		assertThat(Instant.parse(JsonPath.read(history, "$[0].sessions[0].startedAt"))).isEqualTo(mondayMidnight);
	}

	@Test
	void theSameMomentIsGroupedInEachUsersOwnTimezone() throws Exception {
		// Monday 00:30 in Auckland is still Sunday afternoon in Vancouver.
		LocalDate aucklandMonday = thisMonday(AUCKLAND).minusWeeks(2);
		Instant moment = aucklandMonday.atTime(0, 30).atZone(AUCKLAND).toInstant();
		TestBrowser auckland = signedUpIn(AUCKLAND);
		TestBrowser vancouver = signedUpIn(VANCOUVER);
		finishedWorkoutAt(auckland, moment);
		finishedWorkoutAt(vancouver, moment);

		assertThat(weekStarts(auckland.get("/api/history").body())).containsExactly(aucklandMonday.toString());
		assertThat(weekStarts(vancouver.get("/api/history").body()))
			.containsExactly(aucklandMonday.minusWeeks(1).toString());
	}

	@Test
	void changingTheTimezoneRegroupsTheSameWorkouts() throws Exception {
		TestBrowser browser = signedUpIn(AUCKLAND);
		LocalDate aucklandMonday = thisMonday(AUCKLAND).minusWeeks(2);
		finishedWorkoutAt(browser, aucklandMonday.atTime(0, 30).atZone(AUCKLAND).toInstant());

		browser.put("/api/settings", """
			{"splitType": "FB", "units": "KG", "timezone": "America/Vancouver", "equipment": []}""");

		assertThat(weekStarts(browser.get("/api/history").body()))
			.containsExactly(aucklandMonday.minusWeeks(1).toString());
	}

	@Test
	void newestWeekAndNewestWorkoutComeFirstAndEmptyWeeksAreLeftOut() throws Exception {
		TestBrowser browser = signedUpIn(BERLIN);
		LocalDate monday = thisMonday(BERLIN);
		String old = finishedWorkoutAt(browser, at(monday.minusWeeks(4).plusDays(2), 18, BERLIN));
		String earlier = finishedWorkoutAt(browser, at(monday.minusWeeks(1), 7, BERLIN));
		String later = finishedWorkoutAt(browser, at(monday.minusWeeks(1).plusDays(6), 20, BERLIN));

		String history = browser.get("/api/history").body();

		assertThat(weekStarts(history))
			.containsExactly(monday.minusWeeks(1).toString(), monday.minusWeeks(4).toString());
		assertThat(JsonPath.<List<String>>read(history, "$[0].sessions[*].id")).containsExactly(later, earlier);
		assertThat(JsonPath.<List<String>>read(history, "$[1].sessions[*].id")).containsExactly(old);
	}

	@Test
	void weeksLimitsHowFarBackTheHistoryGoes() throws Exception {
		TestBrowser browser = signedUpIn(BERLIN);
		LocalDate monday = thisMonday(BERLIN);
		finishedWorkoutAt(browser, monday.atStartOfDay(BERLIN).toInstant()); // this week
		finishedWorkoutAt(browser, at(monday.minusWeeks(1), 12, BERLIN)); // last week
		finishedWorkoutAt(browser, at(monday.minusWeeks(11), 12, BERLIN)); // 12th week back (included by default)
		finishedWorkoutAt(browser, at(monday.minusWeeks(12), 12, BERLIN)); // 13th week back
		finishedWorkoutAt(browser, at(monday.minusWeeks(51), 12, BERLIN)); // 52nd week back

		assertThat(weekStarts(browser.get("/api/history?weeks=1").body())).containsExactly(monday.toString());
		assertThat(weekStarts(browser.get("/api/history?weeks=2").body()))
			.containsExactly(monday.toString(), monday.minusWeeks(1).toString());
		assertThat(weekStarts(browser.get("/api/history").body())).hasSize(3)
			.endsWith(monday.minusWeeks(11).toString());
		assertThat(weekStarts(browser.get("/api/history?weeks=52").body())).hasSize(5)
			.endsWith(monday.minusWeeks(51).toString());
	}

	@Test
	void weeksOutsideOneTo52Is400() throws Exception {
		TestBrowser browser = signedUpIn(BERLIN);

		for (String weeks : List.of("0", "53", "-1")) {
			HttpResponse<String> response = browser.get("/api/history?weeks=" + weeks);
			assertThat(response.statusCode()).as(weeks).isEqualTo(400);
			assertThat(JsonPath.<Map<String, String>>read(response.body(), "$.fields"))
				.containsOnly(Map.entry("weeks", "must be between 1 and 52"));
		}
		assertThat(browser.get("/api/history?weeks=abc").statusCode()).isEqualTo(400);
	}

	@Test
	void onlyFinishedWorkoutsAreListedAndCountsAreRight() throws Exception {
		TestBrowser browser = signedUpIn(BERLIN);
		// A finished workout: 2 exercises (one without sets), 1 warm-up and 2 working sets done, 1 set not done.
		String id = JsonPath.read(browser.post("/api/sessions", "{}").body(), "$.id");
		browser.post("/api/sessions/" + id + "/exercises", "{\"exerciseId\": \"cable_fly\"}");
		browser.post("/api/sessions/" + id + "/exercises", "{\"exerciseId\": \"push_up\"}");
		completedSet(browser, id, true);
		completedSet(browser, id, false);
		completedSet(browser, id, false);
		browser.post("/api/sessions/" + id + "/exercises/1/sets", "{\"weightKg\": 10, \"reps\": 10}");
		browser.post("/api/sessions/" + id + "/finish", "");
		// An active workout doesn't show up.
		browser.post("/api/sessions", "{}");

		String history = browser.get("/api/history").body();

		assertThat(JsonPath.<List<String>>read(history, "$[*].sessions[*].id")).containsExactly(id);
		assertThat(JsonPath.<Integer>read(history, "$[0].sessions[0].exerciseCount")).isEqualTo(2);
		assertThat(JsonPath.<Integer>read(history, "$[0].sessions[0].workingSets")).isEqualTo(2);
		assertThat(JsonPath.<String>read(history, "$[0].weekStart"))
			.isEqualTo(WeekMath.weekStart(Instant.now(), BERLIN).toString());
	}

	// ---- helpers ----

	private TestBrowser signedUpIn(ZoneId zone) throws Exception {
		TestBrowser browser = new TestBrowser(port);
		browser.signUpNewUser(zone.getId());
		return browser;
	}

	/** Starts and finishes an empty workout, then moves it to start at {@code start} (one hour long). */
	private String finishedWorkoutAt(TestBrowser browser, Instant start) throws Exception {
		String id = JsonPath.read(browser.post("/api/sessions", "{}").body(), "$.id");
		assertThat(browser.post("/api/sessions/" + id + "/finish", "").statusCode()).isEqualTo(200);
		jdbcTemplate.update("update workout_session set started_at = ?, finished_at = ? where id = ?",
			OffsetDateTime.ofInstant(start, ZoneOffset.UTC), OffsetDateTime.ofInstant(start.plusSeconds(3600),
				ZoneOffset.UTC), UUID.fromString(id));
		return id;
	}

	private static void completedSet(TestBrowser browser, String sessionId, boolean warmup) throws Exception {
		String set = browser.post("/api/sessions/" + sessionId + "/exercises/1/sets",
			"{\"weightKg\": 10, \"reps\": 12, \"isWarmup\": " + warmup + "}").body();
		browser.patch("/api/sets/" + JsonPath.read(set, "$.id"), "{\"completed\": true}");
	}

	private static LocalDate thisMonday(ZoneId zone) {
		return WeekMath.weekStart(Instant.now(), zone);
	}

	private static Instant at(LocalDate date, int hour, ZoneId zone) {
		return date.atTime(hour, 0).atZone(zone).toInstant();
	}

	private static List<String> weekStarts(String history) {
		return JsonPath.read(history, "$[*].weekStart");
	}

}
