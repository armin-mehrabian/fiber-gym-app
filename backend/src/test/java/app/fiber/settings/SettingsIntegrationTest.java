package app.fiber.settings;

import static org.assertj.core.api.Assertions.assertThat;

import app.fiber.TestBrowser;
import app.fiber.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * Settings, split days and weekly targets, end to end: real HTTP with a logged-in session,
 * against a real PostgreSQL (Testcontainers, needs Docker). Each test signs up fresh users.
 * The fixture has all 26 regions and the equipment ids barbell, bench and cables.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SettingsIntegrationTest {

	private static final List<String> PUSH = List.of(
		"chest_upper", "chest_mid", "chest_lower", "delt_front", "delt_side", "triceps_long", "triceps_lateral");
	private static final List<String> PULL = List.of(
		"traps_upper", "traps_mid", "lats_upper", "lats_lower", "delt_rear", "biceps_long", "biceps_short",
		"forearms");
	private static final List<String> LEGS = List.of(
		"quads_rectus", "quads_vasti", "adductors", "glutes", "glute_med", "hamstrings", "calves", "lower_back",
		"abs_upper", "abs_lower", "obliques");

	@LocalServerPort
	private int port;

	private TestBrowser browser;

	@BeforeEach
	void signUp() throws Exception {
		browser = new TestBrowser(port);
		browser.signUpNewUser();
	}

	// ---- settings ----

	@Test
	void beforeOnboardingThereIsNoSplitYet() throws Exception {
		HttpResponse<String> response = browser.get("/api/settings");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("""
			{"splitType":null,"units":"KG","timezone":"Europe/Berlin","onboarded":false,\
			"equipment":[],"splitDays":[]}""");
	}

	@Test
	void pplCreatesPushPullLegsAndMarksTheUserOnboarded() throws Exception {
		HttpResponse<String> response = browser.put("/api/settings",
			settingsJson("PPL", "LB", "America/Vancouver", "[\"cables\", \"barbell\"]", null));

		assertThat(response.statusCode()).isEqualTo(200);
		String body = response.body();
		assertThat(JsonPath.<String>read(body, "$.splitType")).isEqualTo("PPL");
		assertThat(JsonPath.<String>read(body, "$.units")).isEqualTo("LB");
		assertThat(JsonPath.<String>read(body, "$.timezone")).isEqualTo("America/Vancouver");
		assertThat(JsonPath.<Boolean>read(body, "$.onboarded")).isTrue();
		assertThat(JsonPath.<List<String>>read(body, "$.equipment")).containsExactly("barbell", "cables");
		assertThat(JsonPath.<List<String>>read(body, "$.splitDays[*].name")).containsExactly("Push", "Pull", "Legs");
		assertThat(JsonPath.<List<Integer>>read(body, "$.splitDays[*].position")).containsExactly(1, 2, 3);
		assertThat(regionsOfDay(body, 0)).containsExactlyInAnyOrderElementsOf(PUSH);
		assertThat(regionsOfDay(body, 1)).containsExactlyInAnyOrderElementsOf(PULL);
		assertThat(regionsOfDay(body, 2)).containsExactlyInAnyOrderElementsOf(LEGS);

		// GET returns the same, and login / me now say onboarded.
		assertThat(browser.get("/api/settings").body()).isEqualTo(body);
		assertThat(JsonPath.<Boolean>read(browser.get("/api/me").body(), "$.onboarded")).isTrue();
	}

	@Test
	void dayRegionsAreSortedInRegionOrder() throws Exception {
		HttpResponse<String> response = browser.put("/api/settings", settingsJson("PPL", "KG", "UTC", "[]", null));

		// delt_rear is listed later in the Pull preset, but comes first in region order.
		assertThat(regionsOfDay(response.body(), 1)).containsExactly("delt_rear", "traps_upper", "traps_mid",
			"lats_upper", "lats_lower", "biceps_long", "biceps_short", "forearms");
	}

	@Test
	void switchingToUpperLowerReplacesTheOldDays() throws Exception {
		String ppl = browser.put("/api/settings", settingsJson("PPL", "KG", "UTC", "[]", null)).body();
		List<String> oldIds = JsonPath.read(ppl, "$.splitDays[*].id");

		HttpResponse<String> response = browser.put("/api/settings", settingsJson("UL", "KG", "UTC", "[]", null));

		assertThat(response.statusCode()).isEqualTo(200);
		String body = response.body();
		assertThat(JsonPath.<String>read(body, "$.splitType")).isEqualTo("UL");
		assertThat(JsonPath.<List<String>>read(body, "$.splitDays[*].name")).containsExactly("Upper", "Lower");
		assertThat(regionsOfDay(body, 0)).hasSize(15).containsAll(PUSH).containsAll(PULL);
		assertThat(regionsOfDay(body, 1)).containsExactlyInAnyOrderElementsOf(LEGS);
		assertThat(JsonPath.<List<String>>read(body, "$.splitDays[*].id")).doesNotContainAnyElementsOf(oldIds);
		assertThat(JsonPath.<List<String>>read(browser.get("/api/settings").body(), "$.splitDays[*].name"))
			.containsExactly("Upper", "Lower");
	}

	// Changing only units or equipment must not throw away the days (past workouts link to them).
	@Test
	void savingTheSameSplitAgainKeepsTheDays() throws Exception {
		String first = browser.put("/api/settings", settingsJson("PPL", "KG", "UTC", "[]", null)).body();

		String second = browser.put("/api/settings", settingsJson("PPL", "LB", "UTC", "[\"bench\"]", null)).body();

		assertThat(JsonPath.<List<String>>read(second, "$.splitDays[*].id"))
			.isEqualTo(JsonPath.read(first, "$.splitDays[*].id"));
		assertThat(JsonPath.<String>read(second, "$.units")).isEqualTo("LB");
	}

	@Test
	void fullBodyIsOneDayWithAll26Regions() throws Exception {
		HttpResponse<String> response = browser.put("/api/settings", settingsJson("FB", "KG", "UTC", "[]", null));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<List<String>>read(response.body(), "$.splitDays[*].name")).containsExactly("Full body");
		assertThat(regionsOfDay(response.body(), 0)).hasSize(26).doesNotHaveDuplicates();
	}

	@Test
	void presetsIgnoreSplitDaysInTheBody() throws Exception {
		HttpResponse<String> response = browser.put("/api/settings",
			settingsJson("FB", "KG", "UTC", "[]", "[{\"name\": \"\", \"regions\": [\"nope\"]}]"));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<List<String>>read(response.body(), "$.splitDays[*].name")).containsExactly("Full body");
	}

	@Test
	void customSplitSavesTheClientsDays() throws Exception {
		String days = """
			[{"name": " Arms ", "regions": ["triceps_long", "biceps_long", "biceps_long"]},
			 {"name": "Everything else", "regions": ["calves", "chest_mid"]}]""";

		HttpResponse<String> response = browser.put("/api/settings", settingsJson("CUSTOM", "KG", "UTC", "[]", days));

		assertThat(response.statusCode()).isEqualTo(200);
		String body = response.body();
		assertThat(JsonPath.<String>read(body, "$.splitType")).isEqualTo("CUSTOM");
		// Name trimmed, duplicate region dropped, regions in region order.
		assertThat(JsonPath.<List<String>>read(body, "$.splitDays[*].name")).containsExactly("Arms", "Everything else");
		assertThat(JsonPath.<List<Integer>>read(body, "$.splitDays[*].position")).containsExactly(1, 2);
		assertThat(regionsOfDay(body, 0)).containsExactly("biceps_long", "triceps_long");
		assertThat(regionsOfDay(body, 1)).containsExactly("chest_mid", "calves");
	}

	@Test
	void customSplitNeeds1To7Days() throws Exception {
		String eightDays = "[" + String.join(",", Collections.nCopies(8,
			"{\"name\": \"Day\", \"regions\": [\"calves\"]}")) + "]";

		for (String days : new String[] { null, "[]", eightDays }) {
			HttpResponse<String> response = browser.put("/api/settings",
				settingsJson("CUSTOM", "KG", "UTC", "[]", days));

			assertThat(response.statusCode()).as(String.valueOf(days)).isEqualTo(400);
			assertThat(fields(response)).as(String.valueOf(days))
				.containsEntry("splitDays", "a custom split needs 1 to 7 days");
		}
	}

	@Test
	void customSplitChecksEveryDayAndSavesNothingWhenInvalid() throws Exception {
		String days = """
			[{"name": "", "regions": ["calves"]},
			 {"name": "%s", "regions": ["calves"]},
			 {"name": "Legs", "regions": []},
			 {"name": "Back", "regions": ["lats_upper", "lats_middle"]}]""".formatted("x".repeat(21));

		HttpResponse<String> response = browser.put("/api/settings", settingsJson("CUSTOM", "KG", "UTC", "[]", days));

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(JsonPath.<String>read(response.body(), "$.error")).isEqualTo("Invalid settings");
		assertThat(fields(response)).containsOnly(
			Map.entry("splitDays[0].name", "must be 1 to 20 characters"),
			Map.entry("splitDays[1].name", "must be 1 to 20 characters"),
			Map.entry("splitDays[2].regions", "needs at least 1 region"),
			Map.entry("splitDays[3].regions", "unknown region: lats_middle"));
		assertThat(JsonPath.<Boolean>read(browser.get("/api/settings").body(), "$.onboarded")).isFalse();
	}

	@Test
	void unknownEquipmentIs400AndSavesNothing() throws Exception {
		HttpResponse<String> response = browser.put("/api/settings",
			settingsJson("PPL", "KG", "UTC", "[\"barbell\", \"jetpack\"]", null));

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(fields(response)).containsOnly(Map.entry("equipment", "unknown equipment: jetpack"));
		assertThat(JsonPath.<Boolean>read(browser.get("/api/settings").body(), "$.onboarded")).isFalse();
	}

	@Test
	void badSplitTypeUnitsAndTimezoneAre400WithFields() throws Exception {
		HttpResponse<String> response = browser.put("/api/settings",
			settingsJson("BRO", "STONE", "Mars/Olympus", "[]", null));

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(fields(response)).containsOnlyKeys("splitType", "units", "timezone");
	}

	@Test
	void missingEquipmentListIs400() throws Exception {
		HttpResponse<String> response = browser.put("/api/settings", """
			{"splitType": "PPL", "units": "KG", "timezone": "UTC"}""");

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(fields(response)).containsOnly(Map.entry("equipment", "is required"));
	}

	// ---- targets ----

	@Test
	void targetsPutChangesOnlyTheSentRegionsAndReturnsTheFullList() throws Exception {
		HttpResponse<String> response = browser.put("/api/targets", """
			[{"regionId": "calves", "weeklySets": 0}, {"regionId": "biceps_long", "weeklySets": 30}]""");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<List<String>>read(response.body(), "$[*].regionId")).hasSize(26).startsWith("chest_upper");
		assertThat(weeklySets(response.body(), "calves")).isZero();
		assertThat(weeklySets(response.body(), "biceps_long")).isEqualTo(30);
		assertThat(weeklySets(response.body(), "chest_upper")).isEqualTo(6); // untouched default
		assertThat(browser.get("/api/targets").body()).isEqualTo(response.body());
	}

	@Test
	void targetsOutsideTheRangeOrForUnknownRegionsAre400AndSaveNothing() throws Exception {
		HttpResponse<String> response = browser.put("/api/targets", """
			[{"regionId": "chest_mid", "weeklySets": 10},
			 {"regionId": "calves", "weeklySets": 31},
			 {"regionId": "glutes", "weeklySets": -1},
			 {"regionId": "hamstrings"},
			 {"regionId": "wings", "weeklySets": 5}]""");

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(fields(response)).containsOnly(
			Map.entry("calves", "must be between 0 and 30"),
			Map.entry("glutes", "must be between 0 and 30"),
			Map.entry("hamstrings", "must be between 0 and 30"),
			Map.entry("wings", "unknown region"));
		// chest_mid was valid, but nothing is saved when any entry is invalid.
		assertThat(weeklySets(browser.get("/api/targets").body(), "chest_mid")).isEqualTo(6);
	}

	@Test
	void targetsBodyMustBeAList() throws Exception {
		HttpResponse<String> response = browser.put("/api/targets", "{\"regionId\": \"calves\"}");

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.body()).isEqualTo("{\"error\":\"Malformed request body\"}");
	}

	// ---- one user can't touch another user's data ----

	@Test
	void usersOnlySeeAndChangeTheirOwnSettingsAndTargets() throws Exception {
		TestBrowser userB = new TestBrowser(port);
		userB.signUpNewUser();

		// A changes everything.
		browser.put("/api/settings", settingsJson("PPL", "LB", "UTC", "[\"barbell\"]", null));
		browser.put("/api/targets", "[{\"regionId\": \"calves\", \"weeklySets\": 20}]");

		// B still has the signup defaults.
		String settingsB = userB.get("/api/settings").body();
		assertThat(JsonPath.<Object>read(settingsB, "$.splitType")).isNull();
		assertThat(JsonPath.<List<Object>>read(settingsB, "$.splitDays")).isEmpty();
		assertThat(weeklySets(userB.get("/api/targets").body(), "calves")).isEqualTo(6);

		// B changes its own; A's stay as they were.
		userB.put("/api/settings", settingsJson("FB", "KG", "UTC", "[]", null));
		userB.put("/api/targets", "[{\"regionId\": \"calves\", \"weeklySets\": 1}]");

		String settingsA = browser.get("/api/settings").body();
		assertThat(JsonPath.<String>read(settingsA, "$.splitType")).isEqualTo("PPL");
		assertThat(JsonPath.<List<String>>read(settingsA, "$.splitDays[*].name")).containsExactly("Push", "Pull", "Legs");
		assertThat(JsonPath.<List<String>>read(settingsA, "$.equipment")).containsExactly("barbell");
		assertThat(weeklySets(browser.get("/api/targets").body(), "calves")).isEqualTo(20);
	}

	@Test
	void writesNeedALogin() throws Exception {
		TestBrowser anonymous = new TestBrowser(port);
		anonymous.get("/api/auth/csrf"); // has a valid CSRF token, but no login

		assertThat(anonymous.put("/api/settings", settingsJson("PPL", "KG", "UTC", "[]", null)).statusCode())
			.isEqualTo(401);
		assertThat(anonymous.put("/api/targets", "[]").statusCode()).isEqualTo(401);
	}

	// ---- helpers ----

	/** A PUT /api/settings body. splitDays = raw JSON, or null to leave it out. */
	private static String settingsJson(String splitType, String units, String timezone, String equipment,
			String splitDays) {
		String json = """
			{"splitType": "%s", "units": "%s", "timezone": "%s", "equipment": %s"""
			.formatted(splitType, units, timezone, equipment);
		return splitDays == null ? json + "}" : json + ", \"splitDays\": " + splitDays + "}";
	}

	private static List<String> regionsOfDay(String settingsJson, int index) {
		return JsonPath.read(settingsJson, "$.splitDays[" + index + "].regions");
	}

	private static Map<String, String> fields(HttpResponse<String> response) {
		return JsonPath.read(response.body(), "$.fields");
	}

	private static int weeklySets(String targetsJson, String regionId) {
		List<Integer> values = JsonPath.read(targetsJson, "$[?(@.regionId == '" + regionId + "')].weeklySets");
		return values.get(0);
	}

}
