package app.fiber.auth;

import static app.fiber.TestBrowser.PASSWORD;
import static app.fiber.TestBrowser.TIMEZONE;
import static app.fiber.TestBrowser.newEmail;
import static app.fiber.TestBrowser.signupJson;
import static org.assertj.core.api.Assertions.assertThat;

import app.fiber.TestBrowser;
import app.fiber.TestcontainersConfiguration;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * With the production setting (SESSION_COOKIE_SECURE=true), both the session cookie and the
 * XSRF-TOKEN cookie are marked Secure, so browsers only send them over HTTPS.
 * Only the Set-Cookie headers are checked here (the test itself talks plain HTTP).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
	properties = "server.servlet.session.cookie.secure=true")
@Import(TestcontainersConfiguration.class)
class SecureCookiesIntegrationTest {

	@LocalServerPort
	private int port;

	@Test
	void bothCookiesAreSecure() throws Exception {
		TestBrowser browser = new TestBrowser(port);

		HttpResponse<String> csrf = browser.get("/api/auth/csrf");
		String csrfToken = setCookieHeader(csrf, "XSRF-TOKEN").replaceFirst("^XSRF-TOKEN=([^;]*).*$", "$1");
		// A Secure cookie isn't kept by the client over plain HTTP, so send the token by hand.
		HttpResponse<String> signup = browser.postWithCsrfToken("/api/auth/signup",
			signupJson(newEmail(), PASSWORD, TIMEZONE), csrfToken);

		assertThat(setCookieHeader(csrf, "XSRF-TOKEN")).containsIgnoringCase("Secure");
		assertThat(signup.statusCode()).isEqualTo(201);
		assertThat(setCookieHeader(signup, "JSESSIONID")).containsIgnoringCase("Secure");
	}

	private static String setCookieHeader(HttpResponse<String> response, String cookieName) {
		return response.headers().allValues("Set-Cookie").stream()
			.filter(header -> header.startsWith(cookieName + "="))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No Set-Cookie for " + cookieName));
	}

}
