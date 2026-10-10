package app.fiber.auth;

import static app.fiber.TestBrowser.PASSWORD;
import static app.fiber.TestBrowser.TIMEZONE;
import static app.fiber.TestBrowser.loginJson;
import static app.fiber.TestBrowser.newEmail;
import static app.fiber.TestBrowser.signupJson;
import static org.assertj.core.api.Assertions.assertThat;

import app.fiber.TestBrowser;
import app.fiber.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
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

/**
 * Signup, login, logout and the session, end to end: real HTTP against the running app
 * and a real PostgreSQL (Testcontainers, needs Docker). Each test signs up its own users.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTest {

	private static final String LOGIN_REQUIRED = "{\"error\":\"Login required\"}";
	private static final String WRONG_LOGIN = "{\"error\":\"Wrong email or password\"}";

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private TestBrowser browser;

	@BeforeEach
	void openBrowser() {
		browser = new TestBrowser(port);
	}

	@Test
	void fullFlowSignupMeLogoutLoginMe() throws Exception {
		String email = newEmail();

		HttpResponse<String> csrf = browser.get("/api/auth/csrf");
		assertThat(csrf.statusCode()).isEqualTo(204);
		assertThat(browser.cookie("XSRF-TOKEN")).isNotBlank();

		HttpResponse<String> signup = browser.post("/api/auth/signup", signupJson(email, PASSWORD, TIMEZONE));
		assertThat(signup.statusCode()).isEqualTo(201);
		String id = JsonPath.read(signup.body(), "$.id");
		assertThat(JsonPath.<String>read(signup.body(), "$.email")).isEqualTo(email);
		assertThat(JsonPath.<Boolean>read(signup.body(), "$.onboarded")).isFalse();

		// Signup logged us in.
		HttpResponse<String> me = browser.get("/api/me");
		assertThat(me.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<Map<String, Object>>read(me.body(), "$"))
			.containsEntry("id", id).containsEntry("email", email).containsEntry("onboarded", false);

		assertThat(browser.post("/api/auth/logout", "").statusCode()).isEqualTo(204);
		HttpResponse<String> afterLogout = browser.get("/api/me");
		assertThat(afterLogout.statusCode()).isEqualTo(401);
		assertThat(afterLogout.body()).isEqualTo(LOGIN_REQUIRED);

		HttpResponse<String> login = browser.post("/api/auth/login", loginJson(email, PASSWORD));
		assertThat(login.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<Map<String, Object>>read(login.body(), "$"))
			.containsEntry("id", id).containsEntry("email", email).containsEntry("onboarded", false);

		assertThat(browser.get("/api/me").statusCode()).isEqualTo(200);
	}

	@Test
	void sessionCookieIsHttpOnlyAndSameSiteLaxWhileCsrfCookieIsReadableByJavaScript() throws Exception {
		HttpResponse<String> csrf = browser.get("/api/auth/csrf");
		String csrfCookie = setCookieHeader(csrf, "XSRF-TOKEN");
		assertThat(csrfCookie).doesNotContainIgnoringCase("HttpOnly");

		HttpResponse<String> signup = browser.post("/api/auth/signup", signupJson(newEmail(), PASSWORD, TIMEZONE));
		String sessionCookie = setCookieHeader(signup, "JSESSIONID");
		assertThat(sessionCookie).containsIgnoringCase("HttpOnly").containsIgnoringCase("SameSite=Lax");
		// Secure is off by default (local HTTP); SESSION_COOKIE_SECURE=true turns it on.
		assertThat(sessionCookie).doesNotContainIgnoringCase("Secure;").doesNotEndWithIgnoringCase("Secure");
		assertThat(csrfCookie).doesNotContainIgnoringCase("Secure;").doesNotEndWithIgnoringCase("Secure");
	}

	// Session fixation: a login must never keep using a session id that existed before it.
	@Test
	void loginGivesTheSessionANewId() throws Exception {
		String email = browser.signUpNewUser();
		String sessionIdBefore = browser.cookie("JSESSIONID");
		assertThat(sessionIdBefore).isNotBlank();

		HttpResponse<String> login = browser.post("/api/auth/login", loginJson(email, PASSWORD));

		assertThat(login.statusCode()).isEqualTo(200);
		assertThat(browser.cookie("JSESSIONID")).isNotBlank().isNotEqualTo(sessionIdBefore);
		assertThat(browser.get("/api/me").statusCode()).isEqualTo(200);
	}

	@Test
	void csrfEndpointIsPublicAndNeedsNoSession() throws Exception {
		HttpResponse<String> csrf = browser.get("/api/auth/csrf");

		assertThat(csrf.statusCode()).isEqualTo(204);
		assertThat(browser.cookie("JSESSIONID")).isNull();
	}

	@Test
	void emailIsStoredLowercaseAndPasswordOnlyAsBcryptHash() throws Exception {
		String email = newEmail();
		browser.get("/api/auth/csrf");

		HttpResponse<String> signup = browser.post("/api/auth/signup",
			signupJson(email.toUpperCase(), PASSWORD, TIMEZONE));

		assertThat(JsonPath.<String>read(signup.body(), "$.email")).isEqualTo(email);
		Map<String, Object> row = jdbcTemplate.queryForMap(
			"select email, password_hash from user_account where email = ?", email);
		assertThat((String) row.get("password_hash")).startsWith("$2").doesNotContain(PASSWORD);
	}

	@Test
	void loginIgnoresEmailCase() throws Exception {
		String email = browser.signUpNewUser();
		browser.post("/api/auth/logout", "");

		HttpResponse<String> login = browser.post("/api/auth/login", loginJson(email.toUpperCase(), PASSWORD));

		assertThat(login.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<String>read(login.body(), "$.email")).isEqualTo(email);
	}

	@Test
	void wrongPasswordAndUnknownEmailGiveTheSame401() throws Exception {
		String email = browser.signUpNewUser();
		TestBrowser stranger = new TestBrowser(port);
		stranger.get("/api/auth/csrf");

		HttpResponse<String> wrongPassword = stranger.post("/api/auth/login", loginJson(email, "not my password"));
		HttpResponse<String> unknownEmail = stranger.post("/api/auth/login", loginJson(newEmail(), PASSWORD));

		assertThat(wrongPassword.statusCode()).isEqualTo(401);
		assertThat(wrongPassword.body()).isEqualTo(WRONG_LOGIN);
		assertThat(unknownEmail.statusCode()).isEqualTo(401);
		assertThat(unknownEmail.body()).isEqualTo(WRONG_LOGIN);
		assertThat(stranger.get("/api/me").statusCode()).isEqualTo(401);
	}

	@Test
	void duplicateEmailIs409IgnoringCase() throws Exception {
		String email = browser.signUpNewUser();
		TestBrowser other = new TestBrowser(port);
		other.get("/api/auth/csrf");

		HttpResponse<String> again = other.post("/api/auth/signup",
			signupJson(email.toUpperCase(), "another password", TIMEZONE));

		assertThat(again.statusCode()).isEqualTo(409);
		assertThat(again.body())
			.isEqualTo("{\"error\":\"Email already registered\",\"fields\":{\"email\":\"already used\"}}");
	}

	@Test
	void invalidSignupIs400AndNamesEveryBadField() throws Exception {
		browser.get("/api/auth/csrf");

		HttpResponse<String> response = browser.post("/api/auth/signup",
			signupJson("not-an-email", "short", "Mars/Olympus"));

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(JsonPath.<String>read(response.body(), "$.error")).isEqualTo("Invalid input");
		assertThat(JsonPath.<Map<String, String>>read(response.body(), "$.fields"))
			.containsOnlyKeys("email", "password", "timezone")
			.containsEntry("password", "must be 8 to 72 characters");
		assertThat(browser.cookie("JSESSIONID")).isNull();
	}

	@Test
	void passwordLongerThan72CharactersIs400() throws Exception {
		browser.get("/api/auth/csrf");

		HttpResponse<String> response = browser.post("/api/auth/signup",
			signupJson(newEmail(), "a".repeat(73), TIMEZONE));

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(JsonPath.<Map<String, String>>read(response.body(), "$.fields")).containsOnlyKeys("password");
	}

	// 72 characters, but "é" takes 2 bytes, so it's more than BCrypt's 72-byte limit.
	@Test
	void passwordOver72BytesIs400NotAServerError() throws Exception {
		browser.get("/api/auth/csrf");

		HttpResponse<String> response = browser.post("/api/auth/signup",
			signupJson(newEmail(), "é".repeat(72), TIMEZONE));

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(JsonPath.<Map<String, String>>read(response.body(), "$.fields"))
			.containsEntry("password", "is too long");
	}

	@Test
	void postsWithoutTheCsrfHeaderAre403() throws Exception {
		browser.get("/api/auth/csrf");

		HttpResponse<String> signup = browser.postWithoutCsrfToken("/api/auth/signup",
			signupJson(newEmail(), PASSWORD, TIMEZONE));
		HttpResponse<String> login = browser.postWithoutCsrfToken("/api/auth/login", loginJson(newEmail(), PASSWORD));

		assertThat(signup.statusCode()).isEqualTo(403);
		assertThat(signup.body()).isEqualTo("{\"error\":\"Missing or invalid CSRF token\"}");
		assertThat(login.statusCode()).isEqualTo(403);
	}

	@Test
	void logoutWithoutTheCsrfHeaderIs403AndKeepsTheSession() throws Exception {
		browser.signUpNewUser();

		assertThat(browser.postWithoutCsrfToken("/api/auth/logout", "").statusCode()).isEqualTo(403);
		assertThat(browser.get("/api/me").statusCode()).isEqualTo(200);
	}

	@Test
	void notLoggedInIs401WithJsonBody() throws Exception {
		for (String path : List.of("/api/me", "/api/settings", "/api/targets")) {
			HttpResponse<String> response = browser.get(path);

			assertThat(response.statusCode()).as(path).isEqualTo(401);
			assertThat(response.body()).as(path).isEqualTo(LOGIN_REQUIRED);
			assertThat(response.headers().firstValue("Content-Type")).get().asString().startsWith("application/json");
		}
	}

	@Test
	void signupCreatesAllTargetsWithTheDefaults() throws Exception {
		browser.signUpNewUser();

		HttpResponse<String> response = browser.get("/api/targets");

		assertThat(response.statusCode()).isEqualTo(200);
		List<String> regions = JsonPath.read(response.body(), "$[*].regionId");
		assertThat(regions).hasSize(26).startsWith("chest_upper", "chest_mid", "chest_lower").endsWith("calves");
		assertThat(JsonPath.<List<String>>read(response.body(), "$[?(@.weeklySets == 6)].regionId"))
			.containsExactlyInAnyOrder("chest_upper", "chest_mid", "chest_lower", "delt_side", "delt_rear",
				"traps_mid", "lats_upper", "lats_lower", "quads_vasti", "glutes", "hamstrings", "calves");
		assertThat(JsonPath.<List<String>>read(response.body(), "$[?(@.weeklySets == 4)].regionId"))
			.containsExactlyInAnyOrder("delt_front", "traps_upper", "lower_back", "biceps_long", "biceps_short",
				"triceps_long", "triceps_lateral", "forearms", "abs_upper", "abs_lower", "obliques",
				"quads_rectus", "adductors", "glute_med");
	}

	/** The raw Set-Cookie header for one cookie (it carries the HttpOnly / SameSite flags). */
	private static String setCookieHeader(HttpResponse<String> response, String cookieName) {
		return response.headers().allValues("Set-Cookie").stream()
			.filter(header -> header.startsWith(cookieName + "="))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No Set-Cookie for " + cookieName));
	}

}
