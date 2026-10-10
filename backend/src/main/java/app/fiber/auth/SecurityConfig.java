package app.fiber.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Who may call what.
 *
 * Public (no login): the health check and reading the catalog (regions, exercise list, one exercise).
 * Everything else needs a logged-in user.
 * Login itself (session cookie, BCrypt, CSRF token) is added in Phase 5.
 */
@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/api/health").permitAll()
				// "/api/exercises/*" matches one path segment only, so a user's
				// /api/exercises/{id}/history (Phase 10) still needs a login.
				.requestMatchers(HttpMethod.GET, "/api/regions", "/api/exercises", "/api/exercises/*").permitAll()
				// Spring renders error responses (e.g. 400 for a bad ?all= value) on /error. Without this,
				// a caller who isn't logged in would get 401 there instead of the real error status.
				.requestMatchers("/error").permitAll()
				.anyRequest().authenticated())
			// Not logged in -> plain 401 (our API contract), instead of a login page or redirect.
			.exceptionHandling(errors -> errors
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
		return http.build();
	}

}
