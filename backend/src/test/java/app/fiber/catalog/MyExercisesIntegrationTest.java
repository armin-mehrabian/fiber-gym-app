package app.fiber.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import app.fiber.TestBrowser;
import app.fiber.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * GET /api/exercises?mine=true end to end, with a logged-in user and their saved equipment.
 *
 * Fixture exercises: Cable fly (cables), Incline barbell press (barbell + bench), Push-up (bodyweight),
 * Single-arm cable row (cables), Barbell floor press (barbell, retired).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class MyExercisesIntegrationTest {

	@LocalServerPort
	private int port;

	private TestBrowser browser;

	@BeforeEach
	void signUp() throws Exception {
		browser = new TestBrowser(port);
		browser.signUpNewUser();
	}

	@Test
	void onlyExercisesTheUserHasAllTheEquipmentForPlusBodyweight() throws Exception {
		saveEquipment("[\"barbell\", \"cables\"]"); // no bench, so the incline press is out

		HttpResponse<String> response = browser.get("/api/exercises?mine=true");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(ids(response)).containsExactly("cable_fly", "push_up", "single_arm_cable_row");
	}

	@Test
	void withAllTheEquipmentEverythingNotRetiredIsListed() throws Exception {
		saveEquipment("[\"barbell\", \"bench\", \"cables\"]");

		assertThat(ids(browser.get("/api/exercises?mine=true")))
			.containsExactly("cable_fly", "incline_barbell_press", "push_up", "single_arm_cable_row");
	}

	@Test
	void withoutEquipmentOnlyBodyweightExercisesAreLeft() throws Exception {
		assertThat(ids(browser.get("/api/exercises?mine=true"))).containsExactly("push_up");
	}

	@Test
	void mineCombinesWithTheSearchAndTheRetiredRule() throws Exception {
		saveEquipment("[\"barbell\", \"cables\"]");

		assertThat(ids(browser.get("/api/exercises?mine=true&q=CABLE")))
			.containsExactly("cable_fly", "single_arm_cable_row");
		assertThat(ids(browser.get("/api/exercises?mine=true&q=press"))).isEmpty();
		// The retired floor press only needs a barbell, so ?all=true brings it back.
		assertThat(ids(browser.get("/api/exercises?mine=true&q=press&all=true")))
			.containsExactly("barbell_floor_press");
	}

	@Test
	void withoutMineTheListIsTheSameForEveryone() throws Exception {
		saveEquipment("[]");

		assertThat(ids(browser.get("/api/exercises")))
			.containsExactly("cable_fly", "incline_barbell_press", "push_up", "single_arm_cable_row");
	}

	@Test
	void mineNeedsALoginButTheListStaysPublic() throws Exception {
		TestBrowser anonymous = new TestBrowser(port);

		HttpResponse<String> mine = anonymous.get("/api/exercises?mine=true");
		assertThat(mine.statusCode()).isEqualTo(401);
		assertThat(mine.body()).isEqualTo("{\"error\":\"Login required\"}");

		assertThat(anonymous.get("/api/exercises").statusCode()).isEqualTo(200);
		assertThat(anonymous.get("/api/exercises?mine=false").statusCode()).isEqualTo(200);
	}

	private void saveEquipment(String equipmentJson) throws Exception {
		HttpResponse<String> response = browser.put("/api/settings", """
			{"splitType": "PPL", "units": "KG", "timezone": "UTC", "equipment": %s}""".formatted(equipmentJson));
		assertThat(response.statusCode()).isEqualTo(200);
	}

	private static List<String> ids(HttpResponse<String> response) {
		assertThat(response.statusCode()).isEqualTo(200);
		return JsonPath.read(response.body(), "$[*].id");
	}

}
