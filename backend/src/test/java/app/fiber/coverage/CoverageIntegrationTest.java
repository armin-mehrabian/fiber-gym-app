package app.fiber.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import app.fiber.TestBrowser;
import app.fiber.TestcontainersConfiguration;
import app.fiber.workout.WeekMath;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * GET /api/coverage/week end to end. Workouts are logged through the real API, then their start
 * and finish times are moved to exact moments with SQL. The moments are relative to the current
 * week, so the tests work on any day they run.
 *
 * Exercises of the test library used here:
 * - Incline barbell press: primary chest_upper + chest_mid, secondary delt_front
 * - Push-up: primary chest_mid, secondary delt_front
 * - Single-arm cable row: primary lats_lower, secondary lats_upper + biceps_long
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CoverageIntegrationTest {

	private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
	private static final ZoneId VANCOUVER = ZoneId.of("America/Vancouver");

	private static final String INCLINE = "incline_barbell_press";
	private static final String PUSH_UP = "push_up";
	private static final String ROW = "single_arm_cable_row";

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void aKnownWeekHasTheExactNumbers() throws Exception {
		TestBrowser browser = signedUpIn(BERLIN);
		onlyTheseTargets(browser, Map.of("chest_upper", 6, "chest_mid", 6, "delt_front", 4, "lats_lower", 6));
		LocalDate monday = thisMonday(BERLIN).minusWeeks(1);

		// Tuesday: incline press with a warm-up and an unfinished set (neither counts), and push-ups.
		String tuesday = startWorkout(browser);
		int incline = addExercise(browser, tuesday, INCLINE);
		logSet(browser, tuesday, incline, true, true); // warm-up
		logCompletedSets(browser, tuesday, incline, 3);
		logSet(browser, tuesday, incline, false, false); // not ticked
		logCompletedSets(browser, tuesday, addExercise(browser, tuesday, PUSH_UP), 2);
		finish(browser, tuesday, at(monday.plusDays(1), 18, BERLIN));
		// Thursday: incline press and rows.
		String thursday = startWorkout(browser);
		logCompletedSets(browser, thursday, addExercise(browser, thursday, INCLINE), 2);
		logCompletedSets(browser, thursday, addExercise(browser, thursday, ROW), 3);
		finish(browser, thursday, at(monday.plusDays(3), 18, BERLIN));

		// Any day of the week finds the same week.
		HttpResponse<String> response = browser.get("/api/coverage/week?date=" + monday.plusDays(2));

		assertThat(response.statusCode()).isEqualTo(200);
		String json = response.body();
		assertThat(JsonPath.<Map<String, Object>>read(json, "$")).containsOnlyKeys("weekStart", "weekEnd",
			"weekPercent", "days", "regions");
		assertThat(JsonPath.<String>read(json, "$.weekStart")).isEqualTo(monday.toString());
		assertThat(JsonPath.<String>read(json, "$.weekEnd")).isEqualTo(monday.plusDays(6).toString());
		assertThat(sessionsPerDay(json)).containsExactly(0, 1, 0, 1, 0, 0, 0);
		assertThat(JsonPath.<List<String>>read(json, "$.days[*].date")).first().isEqualTo(monday.toString());

		// All regions, in region order (the same order as the targets list).
		assertThat(JsonPath.<List<String>>read(json, "$.regions[*].regionId"))
			.isEqualTo(JsonPath.<List<String>>read(browser.get("/api/targets").body(), "$[*].regionId"))
			.hasSize(26);
		assertThat(region(json, "chest_upper")).containsOnlyKeys("regionId", "effective", "target", "coverage",
			"recovering", "contributions");

		// 3 + 2 sets of incline press; 5 of 6 = 0.83.
		assertRegion(json, "chest_upper", 5.0, 6, 0.83);
		assertThat(contributions(json, "chest_upper")).containsExactly(
			contribution("Incline barbell press", "Tue", 3.0),
			contribution("Incline barbell press", "Thu", 2.0));
		// 3 + 2 + 2 = 7 sets, but coverage stops at 1.
		assertRegion(json, "chest_mid", 7.0, 6, 1.0);
		assertThat(contributions(json, "chest_mid")).containsExactly(
			contribution("Incline barbell press", "Tue", 3.0),
			contribution("Push-up", "Tue", 2.0),
			contribution("Incline barbell press", "Thu", 2.0));
		// Helping region: (3 + 2 + 2) x 0.5 = 3.5 of 4 = 0.875, rounded to 0.88.
		assertRegion(json, "delt_front", 3.5, 4, 0.88);
		assertThat(contributions(json, "delt_front")).containsExactly(
			contribution("Incline barbell press", "Tue", 1.5),
			contribution("Push-up", "Tue", 1.0),
			contribution("Incline barbell press", "Thu", 1.0));
		assertRegion(json, "lats_lower", 3.0, 6, 0.5);
		// Target 0: the sets still show, but the coverage is 0 and it's left out of the week percent.
		assertRegion(json, "biceps_long", 1.5, 0, 0.0);
		assertThat(contributions(json, "biceps_long")).containsExactly(
			contribution("Single-arm cable row", "Thu", 1.5));
		assertRegion(json, "calves", 0.0, 0, 0.0);
		assertThat(contributions(json, "calves")).isEmpty();

		// (5/6 + 1 + 0.875 + 0.5) / 4 regions with a target = 80%
		assertThat(JsonPath.<Integer>read(json, "$.weekPercent")).isEqualTo(80);
		// Those workouts are days ago, so nothing is recovering.
		assertThat(recoveringRegions(json)).isEmpty();
	}

	@Test
	void withoutADateItIsTheCurrentWeekInTheUsersTimezone() throws Exception {
		TestBrowser browser = signedUpIn(BERLIN);
		String id = startWorkout(browser);
		logCompletedSets(browser, id, addExercise(browser, id, INCLINE), 3);
		assertThat(browser.post("/api/sessions/" + id + "/finish", "").statusCode()).isEqualTo(200);
		LocalDate today = LocalDate.now(BERLIN);

		String json = browser.get("/api/coverage/week").body();

		assertThat(JsonPath.<String>read(json, "$.weekStart")).isEqualTo(WeekMath.weekStart(today).toString());
		assertThat(JsonPath.<List<Integer>>read(json, "$.days[?(@.date == '" + today + "')].sessions"))
			.containsExactly(1);
		assertRegion(json, "chest_upper", 3.0, 6, 0.5);
		assertThat(contributions(json, "chest_upper")).containsExactly(
			contribution("Incline barbell press", CoverageService.shortDayName(today), 3.0));
		// Just finished with 3 sets: both chest regions need rest, the front delts (1.5) don't.
		assertThat(recoveringRegions(json)).containsExactlyInAnyOrder("chest_upper", "chest_mid");
	}

	@Test
	void aNewUserHasAnEmptyWeek() throws Exception {
		TestBrowser browser = signedUpIn(BERLIN);

		String json = browser.get("/api/coverage/week").body();

		assertThat(JsonPath.<Integer>read(json, "$.weekPercent")).isZero();
		assertThat(sessionsPerDay(json)).hasSize(7).containsOnly(0);
		assertThat(JsonPath.<List<Double>>read(json, "$.regions[*].effective")).hasSize(26).containsOnly(0.0);
		assertThat(JsonPath.<List<Double>>read(json, "$.regions[*].coverage")).containsOnly(0.0);
		assertThat(JsonPath.<List<Object>>read(json, "$.regions[*].contributions[*]")).isEmpty();
		assertThat(recoveringRegions(json)).isEmpty();
	}

	@Test
	void aWorkoutInProgressDoesNotCount() throws Exception {
		TestBrowser browser = signedUpIn(BERLIN);
		String id = startWorkout(browser);
		logCompletedSets(browser, id, addExercise(browser, id, INCLINE), 4);

		String json = browser.get("/api/coverage/week").body();

		assertRegion(json, "chest_upper", 0.0, 6, 0.0);
		assertThat(sessionsPerDay(json)).containsOnly(0);
		assertThat(recoveringRegions(json)).isEmpty();
	}

	@Test
	void anotherUsersWorkoutsDoNotCount() throws Exception {
		TestBrowser someoneElse = signedUpIn(BERLIN);
		String id = startWorkout(someoneElse);
		logCompletedSets(someoneElse, id, addExercise(someoneElse, id, INCLINE), 4);
		someoneElse.post("/api/sessions/" + id + "/finish", "");
		TestBrowser me = signedUpIn(BERLIN);

		String mine = me.get("/api/coverage/week").body();

		assertRegion(mine, "chest_upper", 0.0, 6, 0.0);
		assertThat(sessionsPerDay(mine)).containsOnly(0);
		assertThat(recoveringRegions(mine)).isEmpty();
		// ...while the other user does see it.
		assertRegion(someoneElse.get("/api/coverage/week").body(), "chest_upper", 4.0, 6, 0.67);
	}

	@Test
	void theWeekEndsAtSundayMidnightInTheUsersTimezone() throws Exception {
		// In Vancouver, Sunday 23:30 and Monday 00:30 are both Monday in UTC, but in different weeks.
		TestBrowser browser = signedUpIn(VANCOUVER);
		LocalDate monday = thisMonday(VANCOUVER).minusWeeks(1);
		String sunday = startWorkout(browser);
		logCompletedSets(browser, sunday, addExercise(browser, sunday, PUSH_UP), 2);
		finish(browser, sunday, monday.minusDays(1).atTime(23, 30).atZone(VANCOUVER).toInstant());
		String mondayWorkout = startWorkout(browser);
		logCompletedSets(browser, mondayWorkout, addExercise(browser, mondayWorkout, PUSH_UP), 3);
		finish(browser, mondayWorkout, monday.atTime(0, 30).atZone(VANCOUVER).toInstant());

		String thisWeek = browser.get("/api/coverage/week?date=" + monday).body();
		String weekBefore = browser.get("/api/coverage/week?date=" + monday.minusDays(1)).body();

		assertThat(JsonPath.<String>read(thisWeek, "$.weekStart")).isEqualTo(monday.toString());
		assertThat(sessionsPerDay(thisWeek)).containsExactly(1, 0, 0, 0, 0, 0, 0);
		assertThat(contributions(thisWeek, "chest_mid")).containsExactly(contribution("Push-up", "Mon", 3.0));

		assertThat(JsonPath.<String>read(weekBefore, "$.weekStart")).isEqualTo(monday.minusWeeks(1).toString());
		assertThat(sessionsPerDay(weekBefore)).containsExactly(0, 0, 0, 0, 0, 0, 1);
		assertThat(contributions(weekBefore, "chest_mid")).containsExactly(contribution("Push-up", "Sun", 2.0));
	}

	@Test
	void aFinishedWorkoutWithoutSetsStillCountsAsATrainingDay() throws Exception {
		TestBrowser browser = signedUpIn(BERLIN);
		LocalDate monday = thisMonday(BERLIN).minusWeeks(2);
		finish(browser, startWorkout(browser), at(monday.plusDays(4), 9, BERLIN));
		finish(browser, startWorkout(browser), at(monday.plusDays(4), 17, BERLIN));

		String json = browser.get("/api/coverage/week?date=" + monday).body();

		assertThat(sessionsPerDay(json)).containsExactly(0, 0, 0, 0, 2, 0, 0);
		assertThat(JsonPath.<Integer>read(json, "$.weekPercent")).isZero();
	}

	@Test
	void anInvalidDateIs400() throws Exception {
		TestBrowser browser = signedUpIn(BERLIN);

		for (String date : List.of("2026-02-30", "2026-13-01", "abc", "05%2F10%2F2026")) {
			HttpResponse<String> response = browser.get("/api/coverage/week?date=" + date);
			assertThat(response.statusCode()).as(date).isEqualTo(400);
			assertThat(JsonPath.<String>read(response.body(), "$.error")).as(date).isNotBlank();
		}
	}

	@Test
	void aDateWhoseWeekLeavesTheYears1To9999Is400() throws Exception {
		TestBrowser browser = signedUpIn(BERLIN);

		// 9999-12-27 is a Monday: its week would end on 10000-01-02. 0000-12-31 is in the week of 0000-12-25.
		// The last two are the most extreme dates Java can read; they must not break the week maths.
		for (String date : List.of("9999-12-27", "9999-12-31", "0000-12-31", "%2B300000-01-01",
				"%2B999999999-12-31", "-999999999-01-01")) {
			HttpResponse<String> response = browser.get("/api/coverage/week?date=" + date);
			assertThat(response.statusCode()).as(date).isEqualTo(400);
			assertThat(JsonPath.<String>read(response.body(), "$.error")).as(date).isEqualTo("Invalid input");
			assertThat(JsonPath.<String>read(response.body(), "$.fields.date")).as(date)
				.isEqualTo("its week must lie between 0001-01-01 and 9999-12-31");
		}
	}

	@Test
	void theFirstAndLastAllowedWeeksWork() throws Exception {
		TestBrowser browser = signedUpIn(BERLIN);

		// 9999-12-26 is a Sunday: the last week that ends within 4-digit years.
		HttpResponse<String> last = browser.get("/api/coverage/week?date=9999-12-26");
		assertThat(last.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<String>read(last.body(), "$.weekStart")).isEqualTo("9999-12-20");
		assertThat(JsonPath.<String>read(last.body(), "$.weekEnd")).isEqualTo("9999-12-26");

		// 0001-01-01 is a Monday, so its week starts on that very day.
		HttpResponse<String> first = browser.get("/api/coverage/week?date=0001-01-01");
		assertThat(first.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<String>read(first.body(), "$.weekStart")).isEqualTo("0001-01-01");
		assertThat(JsonPath.<String>read(first.body(), "$.weekEnd")).isEqualTo("0001-01-07");
	}

	@Test
	void anEmptyDateMeansTheCurrentWeek() throws Exception {
		TestBrowser browser = signedUpIn(BERLIN);

		HttpResponse<String> response = browser.get("/api/coverage/week?date=");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<String>read(response.body(), "$.weekStart"))
			.isEqualTo(thisMonday(BERLIN).toString());
	}

	@Test
	void withoutLoginIs401() throws Exception {
		HttpResponse<String> response = new TestBrowser(port).get("/api/coverage/week");

		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(JsonPath.<String>read(response.body(), "$.error")).isEqualTo("Login required");
	}

	@Test
	void aHardWorkout47HoursAgoMakesRegionsRecovering() throws Exception {
		TestBrowser browser = signedUpIn(BERLIN);
		// One workout: chest_mid gets 2 (incline) + 1 (push-up) = 3, chest_upper only 2, delt_front 1.5.
		String id = startWorkout(browser);
		logCompletedSets(browser, id, addExercise(browser, id, INCLINE), 2);
		logCompletedSets(browser, id, addExercise(browser, id, PUSH_UP), 1);
		finishedHoursAgo(browser, id, 47);

		assertThat(recoveringRegions(browser.get("/api/coverage/week").body())).containsExactly("chest_mid");
		// Recovery is about the last 48 hours, whatever week is shown.
		assertThat(recoveringRegions(browser.get("/api/coverage/week?date=2020-01-06").body()))
			.containsExactly("chest_mid");
	}

	@Test
	void aHardWorkout49HoursAgoNoLongerCounts() throws Exception {
		TestBrowser browser = signedUpIn(BERLIN);
		String id = startWorkout(browser);
		logCompletedSets(browser, id, addExercise(browser, id, INCLINE), 4);
		finishedHoursAgo(browser, id, 49);

		assertThat(recoveringRegions(browser.get("/api/coverage/week").body())).isEmpty();
	}

	@Test
	void twoLightWorkoutsDoNotAddUpToRecovering() throws Exception {
		TestBrowser browser = signedUpIn(BERLIN);
		for (int hoursAgo : List.of(20, 10)) {
			String id = startWorkout(browser);
			logCompletedSets(browser, id, addExercise(browser, id, INCLINE), 2);
			finishedHoursAgo(browser, id, hoursAgo);
		}

		assertThat(recoveringRegions(browser.get("/api/coverage/week").body())).isEmpty();
	}

	// ---- helpers: logging workouts ----

	private TestBrowser signedUpIn(ZoneId zone) throws Exception {
		TestBrowser browser = new TestBrowser(port);
		browser.signUpNewUser(zone.getId());
		return browser;
	}

	/** Sets every target to 0, except the given ones. */
	private static void onlyTheseTargets(TestBrowser browser, Map<String, Integer> targets) throws Exception {
		List<String> regionIds = JsonPath.read(browser.get("/api/targets").body(), "$[*].regionId");
		String body = regionIds.stream()
			.map(id -> "{\"regionId\": \"%s\", \"weeklySets\": %d}".formatted(id, targets.getOrDefault(id, 0)))
			.collect(Collectors.joining(",", "[", "]"));
		assertThat(browser.put("/api/targets", body).statusCode()).isEqualTo(200);
	}

	private static String startWorkout(TestBrowser browser) throws Exception {
		HttpResponse<String> response = browser.post("/api/sessions", "{}");
		assertThat(response.statusCode()).isEqualTo(201);
		return JsonPath.read(response.body(), "$.id");
	}

	/** Adds the exercise at the end of the workout and returns its position. */
	private static int addExercise(TestBrowser browser, String sessionId, String exerciseId) throws Exception {
		String session = browser.post("/api/sessions/" + sessionId + "/exercises",
			"{\"exerciseId\": \"" + exerciseId + "\"}").body();
		return JsonPath.read(session, "$.exercises.length()");
	}

	private static void logCompletedSets(TestBrowser browser, String sessionId, int position, int count)
			throws Exception {
		for (int i = 0; i < count; i++) {
			logSet(browser, sessionId, position, false, true);
		}
	}

	private static void logSet(TestBrowser browser, String sessionId, int position, boolean warmup,
			boolean completed) throws Exception {
		HttpResponse<String> set = browser.post("/api/sessions/" + sessionId + "/exercises/" + position + "/sets",
			"{\"weightKg\": 40, \"reps\": 10, \"isWarmup\": " + warmup + "}");
		assertThat(set.statusCode()).isEqualTo(201);
		if (completed) {
			browser.patch("/api/sets/" + JsonPath.read(set.body(), "$.id"), "{\"completed\": true}");
		}
	}

	/** Finishes the workout, then moves it to start at {@code start} (one hour long). */
	private void finish(TestBrowser browser, String sessionId, Instant start) throws Exception {
		moveFinishedWorkout(browser, sessionId, start, start.plus(Duration.ofHours(1)));
	}

	/** Finishes the workout, then moves it to have finished {@code hours} hours ago (one hour long). */
	private void finishedHoursAgo(TestBrowser browser, String sessionId, int hours) throws Exception {
		Instant finished = Instant.now().minus(Duration.ofHours(hours));
		moveFinishedWorkout(browser, sessionId, finished.minus(Duration.ofHours(1)), finished);
	}

	private void moveFinishedWorkout(TestBrowser browser, String sessionId, Instant start, Instant end)
			throws Exception {
		assertThat(browser.post("/api/sessions/" + sessionId + "/finish", "").statusCode()).isEqualTo(200);
		jdbcTemplate.update("update workout_session set started_at = ?, finished_at = ? where id = ?",
			OffsetDateTime.ofInstant(start, ZoneOffset.UTC), OffsetDateTime.ofInstant(end, ZoneOffset.UTC),
			UUID.fromString(sessionId));
	}

	// ---- helpers: reading the answer ----

	private static LocalDate thisMonday(ZoneId zone) {
		return WeekMath.weekStart(Instant.now(), zone);
	}

	private static Instant at(LocalDate date, int hour, ZoneId zone) {
		return date.atTime(hour, 0).atZone(zone).toInstant();
	}

	private static List<Integer> sessionsPerDay(String json) {
		return JsonPath.read(json, "$.days[*].sessions");
	}

	private static Map<String, Object> region(String json, String regionId) {
		List<Map<String, Object>> matches = JsonPath.read(json, "$.regions[?(@.regionId == '" + regionId + "')]");
		assertThat(matches).as(regionId).hasSize(1);
		return matches.getFirst();
	}

	private static void assertRegion(String json, String regionId, double effective, int target, double coverage) {
		Map<String, Object> region = region(json, regionId);
		assertThat(((Number) region.get("effective")).doubleValue()).as(regionId + " effective").isEqualTo(effective);
		assertThat(region.get("target")).as(regionId + " target").isEqualTo(target);
		assertThat(((Number) region.get("coverage")).doubleValue()).as(regionId + " coverage").isEqualTo(coverage);
	}

	private static List<Map<String, Object>> contributions(String json, String regionId) {
		return JsonPath.read(json, "$.regions[?(@.regionId == '" + regionId + "')].contributions[*]");
	}

	private static Map<String, Object> contribution(String exerciseName, String day, double sets) {
		return Map.of("exerciseName", exerciseName, "day", day, "sets", sets);
	}

	private static Set<String> recoveringRegions(String json) {
		List<String> ids = JsonPath.read(json, "$.regions[?(@.recovering == true)].regionId");
		return Set.copyOf(ids);
	}

}
