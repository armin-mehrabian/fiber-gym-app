package app.fiber.health;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.fiber.auth.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web layer only: the database check is mocked. Our SecurityConfig is imported so the
 * test proves /api/health is reachable without logging in.
 */
// Without the database, our own UserDetailsService isn't loaded here, so Spring Boot would
// create a demo user and log a generated password. Not needed: these tests never log in.
@WebMvcTest(controllers = HealthController.class, excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import(SecurityConfig.class)
class HealthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private HealthService healthService;

	@Test
	void returns200AndUpWhenDatabaseAnswers() throws Exception {
		when(healthService.isDatabaseUp()).thenReturn(true);

		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ok"))
			.andExpect(jsonPath("$.database").value("up"));
	}

	@Test
	void returns503AndDownWhenDatabaseDoesNotAnswer() throws Exception {
		when(healthService.isDatabaseUp()).thenReturn(false);

		mockMvc.perform(get("/api/health"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.status").value("degraded"))
			.andExpect(jsonPath("$.database").value("down"));
	}

	@Test
	void otherEndpointsNeedLoginAndSay401InJson() throws Exception {
		mockMvc.perform(get("/api/settings"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().json("""
				{ "error": "Login required" }""", JsonCompareMode.STRICT));
	}

}
