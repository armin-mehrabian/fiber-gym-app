package app.fiber;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Short steps for integration tests that log workouts through the real API, so each test reads
 * like what a user does: pick a split, start, add an exercise, log sets, finish.
 */
public final class TestWorkouts {

	private TestWorkouts() {
	}

	/** Saves the settings (split + equipment, kg) and returns the settings JSON. */
	public static String chooseSplit(TestBrowser browser, String splitType, String... equipment) throws Exception {
		String equipmentJson = Arrays.stream(equipment).map(id -> "\"" + id + "\"")
			.collect(Collectors.joining(",", "[", "]"));
		HttpResponse<String> response = browser.put("/api/settings", """
			{"splitType": "%s", "units": "KG", "timezone": "%s", "equipment": %s}"""
			.formatted(splitType, TestBrowser.TIMEZONE, equipmentJson));
		assertThat(response.statusCode()).isEqualTo(200);
		return response.body();
	}

	/** The id of the split day with this name, from the settings JSON. */
	public static String splitDayId(String settingsJson, String name) {
		List<String> ids = JsonPath.read(settingsJson, "$.splitDays[?(@.name == '" + name + "')].id");
		assertThat(ids).as(name).hasSize(1);
		return ids.getFirst();
	}

	/** Sets every target to 0, except the given ones. */
	public static void onlyTheseTargets(TestBrowser browser, Map<String, Integer> targets) throws Exception {
		List<String> regionIds = JsonPath.read(browser.get("/api/targets").body(), "$[*].regionId");
		String body = regionIds.stream()
			.map(id -> "{\"regionId\": \"%s\", \"weeklySets\": %d}".formatted(id, targets.getOrDefault(id, 0)))
			.collect(Collectors.joining(",", "[", "]"));
		assertThat(browser.put("/api/targets", body).statusCode()).isEqualTo(200);
	}

	/** Starts an empty workout and returns its id. */
	public static String startWorkout(TestBrowser browser) throws Exception {
		return startWorkout(browser, "{}");
	}

	public static String startWorkout(TestBrowser browser, String body) throws Exception {
		HttpResponse<String> response = browser.post("/api/sessions", body);
		assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
		return JsonPath.read(response.body(), "$.id");
	}

	/** Adds the exercise at the end of the workout and returns the session JSON. */
	public static String addExercise(TestBrowser browser, String sessionId, String exerciseId) throws Exception {
		HttpResponse<String> response = browser.post("/api/sessions/" + sessionId + "/exercises",
			"{\"exerciseId\": \"" + exerciseId + "\"}");
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		return response.body();
	}

	/** Logs and ticks one working set per reps value, all with the same weight, for the exercise at this position. */
	public static void logWorkingSets(TestBrowser browser, String sessionId, int position, String weightKg,
			int... reps) throws Exception {
		for (int r : reps) {
			logSet(browser, sessionId, position, weightKg, r, false, true);
		}
	}

	/** Logs a set; ticks it as done if {@code completed}. Returns the set id. */
	public static String logSet(TestBrowser browser, String sessionId, int position, String weightKg, int reps,
			boolean warmup, boolean completed) throws Exception {
		HttpResponse<String> set = browser.post("/api/sessions/" + sessionId + "/exercises/" + position + "/sets",
			"{\"weightKg\": %s, \"reps\": %d, \"isWarmup\": %b}".formatted(weightKg, reps, warmup));
		assertThat(set.statusCode()).as(set.body()).isEqualTo(201);
		String setId = JsonPath.read(set.body(), "$.id");
		if (completed) {
			assertThat(browser.patch("/api/sets/" + setId, "{\"completed\": true}").statusCode()).isEqualTo(200);
		}
		return setId;
	}

	public static void finish(TestBrowser browser, String sessionId) throws Exception {
		assertThat(browser.post("/api/sessions/" + sessionId + "/finish", "").statusCode()).isEqualTo(200);
	}

	/** Finishes the workout, then moves its start and finish to exact moments. */
	public static void finishAt(TestBrowser browser, JdbcTemplate jdbc, String sessionId, Instant start,
			Instant end) throws Exception {
		finish(browser, sessionId);
		jdbc.update("update workout_session set started_at = ?, finished_at = ? where id = ?",
			OffsetDateTime.ofInstant(start, ZoneOffset.UTC), OffsetDateTime.ofInstant(end, ZoneOffset.UTC),
			UUID.fromString(sessionId));
	}

}
