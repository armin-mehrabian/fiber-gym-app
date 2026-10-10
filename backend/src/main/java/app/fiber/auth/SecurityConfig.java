package app.fiber.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;

/**
 * Who may call what, and how logins and CSRF tokens work.
 *
 * Public (no login): the health check, getting a CSRF token, signup, login, reading the catalog
 * (regions, equipment, exercise list, one exercise) and Spring's /error page. Everything else needs a login.
 *
 * Login: a server-side session, identified by the JSESSIONID cookie (httpOnly, SameSite=Lax;
 * set in application.yml). AuthController starts and ends sessions.
 */
@Configuration
public class SecurityConfig {

	private static final String LOGIN_REQUIRED = "{\"error\":\"Login required\"}";
	private static final String BAD_CSRF_TOKEN = "{\"error\":\"Missing or invalid CSRF token\"}";
	private static final String ACCESS_DENIED = "{\"error\":\"Access denied\"}";

	/**
	 * @param secureCookies true behind HTTPS in production (env SESSION_COOKIE_SECURE). The same setting
	 *                      as the session cookie, so both cookies behave alike.
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			@Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/csrf").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
				// "/api/exercises/*" matches one path segment only, so a user's
				// /api/exercises/{id}/history (Phase 10) still needs a login.
				.requestMatchers(HttpMethod.GET, "/api/regions", "/api/equipment", "/api/exercises", "/api/exercises/*")
					.permitAll()
				// Spring renders error responses (e.g. 400 for a bad ?all= value) on /error. Without this,
				// a caller who isn't logged in would get 401 there instead of the real error status.
				.requestMatchers("/error").permitAll()
				.anyRequest().authenticated())
			// CSRF protection for a single-page app (cookie-to-header pattern):
			// the server sets an XSRF-TOKEN cookie that JavaScript can read, and the frontend sends
			// its value back in the X-XSRF-TOKEN header on every POST/PUT/PATCH/DELETE. Another site
			// can't read our cookie, so it can't send a valid header. This also covers signup and login.
			.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfCookieRepository(secureCookies)))
			.exceptionHandling(errors -> errors
				// Not logged in -> 401 with our JSON error body, instead of a login page or redirect.
				.authenticationEntryPoint((request, response, e) -> writeError(response, 401, LOGIN_REQUIRED))
				// Missing or wrong CSRF token -> 403 with a JSON error body.
				.accessDeniedHandler((request, response, e) ->
					writeError(response, 403, e instanceof CsrfException ? BAD_CSRF_TOKEN : ACCESS_DENIED)))
			// Don't remember which page an anonymous caller wanted (that's for redirecting after a
			// login page, which an API doesn't have). It would also create a session for every 401.
			.requestCache(cache -> cache.disable())
			// Logout is POST /api/auth/logout in AuthController, so Spring's default /logout is turned off.
			.logout(logout -> logout.disable());
		return http.build();
	}

	/**
	 * The XSRF-TOKEN cookie, readable by JavaScript (the same repository spa() sets up).
	 * When secureCookies is false, Spring still marks the cookie Secure if the request itself came over HTTPS.
	 */
	private static CookieCsrfTokenRepository csrfCookieRepository(boolean secureCookies) {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		if (secureCookies) {
			repository.setCookieCustomizer(cookie -> cookie.secure(true));
		}
		return repository;
	}

	private static void writeError(HttpServletResponse response, int status, String json) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(json);
	}

}
