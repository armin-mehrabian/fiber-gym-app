package app.fiber;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.UUID;

/**
 * A tiny browser for integration tests: real HTTP calls to the running app, with its own cookie jar.
 * So the session cookie (JSESSIONID) and the XSRF-TOKEN cookie work like in a real browser.
 * Like the frontend, it sends the XSRF-TOKEN cookie's value back in the X-XSRF-TOKEN header on POST/PUT/PATCH/DELETE.
 * Two instances = two different people.
 */
public class TestBrowser {

	public static final String PASSWORD = "correct horse";
	public static final String TIMEZONE = "Europe/Berlin";

	private final String baseUrl;
	private final CookieManager cookieJar = new CookieManager();
	private final HttpClient http;

	public TestBrowser(int port) {
		this.baseUrl = "http://localhost:" + port;
		this.http = HttpClient.newBuilder().cookieHandler(cookieJar).build();
	}

	/** A new, unique email, so tests never clash with users created by other tests. */
	public static String newEmail() {
		return "user-" + UUID.randomUUID() + "@fiber.app";
	}

	public static String signupJson(String email, String password, String timezone) {
		return """
			{"email": "%s", "password": "%s", "timezone": "%s"}""".formatted(email, password, timezone);
	}

	public static String loginJson(String email, String password) {
		return """
			{"email": "%s", "password": "%s"}""".formatted(email, password);
	}

	/** Gets a CSRF token and signs up a new user; this browser is then logged in. Returns the email. */
	public String signUpNewUser() throws IOException, InterruptedException {
		return signUpNewUser(TIMEZONE);
	}

	/** Like {@link #signUpNewUser()}, with the user's timezone chosen by the test. */
	public String signUpNewUser(String timezone) throws IOException, InterruptedException {
		String email = newEmail();
		get("/api/auth/csrf");
		HttpResponse<String> response = post("/api/auth/signup", signupJson(email, PASSWORD, timezone));
		if (response.statusCode() != 201) {
			throw new IllegalStateException("Signup failed: " + response.statusCode() + " " + response.body());
		}
		return email;
	}

	public HttpResponse<String> get(String path) throws IOException, InterruptedException {
		return send(request(path).GET(), false);
	}

	public HttpResponse<String> post(String path, String json) throws IOException, InterruptedException {
		return send(request(path).POST(BodyPublishers.ofString(json)), true);
	}

	public HttpResponse<String> put(String path, String json) throws IOException, InterruptedException {
		return send(request(path).PUT(BodyPublishers.ofString(json)), true);
	}

	public HttpResponse<String> patch(String path, String json) throws IOException, InterruptedException {
		return send(request(path).method("PATCH", BodyPublishers.ofString(json)), true);
	}

	public HttpResponse<String> delete(String path) throws IOException, InterruptedException {
		return send(request(path).DELETE(), true);
	}

	/** A POST like a forged request from another site would send: cookies, but no X-XSRF-TOKEN header. */
	public HttpResponse<String> postWithoutCsrfToken(String path, String json)
			throws IOException, InterruptedException {
		return send(request(path).POST(BodyPublishers.ofString(json)), false);
	}

	/** A POST with a CSRF token given by hand, for when the client couldn't keep the cookie. */
	public HttpResponse<String> postWithCsrfToken(String path, String json, String csrfToken)
			throws IOException, InterruptedException {
		return send(request(path).header("X-XSRF-TOKEN", csrfToken).header("Cookie", "XSRF-TOKEN=" + csrfToken)
			.POST(BodyPublishers.ofString(json)), false);
	}

	/** The value of a cookie in this browser's jar, or null. */
	public String cookie(String name) {
		return cookieJar.getCookieStore().getCookies().stream()
			.filter(cookie -> cookie.getName().equals(name))
			.map(HttpCookie::getValue)
			.findFirst()
			.orElse(null);
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create(baseUrl + path)).header("Content-Type", "application/json");
	}

	private HttpResponse<String> send(HttpRequest.Builder request, boolean withCsrfToken)
			throws IOException, InterruptedException {
		String csrfToken = cookie("XSRF-TOKEN");
		if (withCsrfToken && csrfToken != null) {
			request.header("X-XSRF-TOKEN", csrfToken);
		}
		return http.send(request.build(), BodyHandlers.ofString());
	}

}
