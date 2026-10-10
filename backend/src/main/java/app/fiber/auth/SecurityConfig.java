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
 * Phase 3: only the health check is public; everything else needs a logged-in user.
 * Login itself (session cookie, BCrypt, CSRF token) is added in Phase 5.
 */
@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/api/health").permitAll()
				.anyRequest().authenticated())
			// Not logged in -> plain 401 (our API contract), instead of a login page or redirect.
			.exceptionHandling(errors -> errors
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
		return http.build();
	}

}
