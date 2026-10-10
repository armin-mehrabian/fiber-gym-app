package app.fiber.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Plain unit test: JdbcTemplate is mocked, no Spring context, no database.
 */
class HealthServiceTest {

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
	private final HealthService healthService = new HealthService(jdbcTemplate);

	@Test
	void databaseIsUpWhenSelectOneWorks() {
		when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

		assertThat(healthService.isDatabaseUp()).isTrue();
	}

	@Test
	void databaseIsDownWhenTheQueryThrows() {
		when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
			.thenThrow(new CannotGetJdbcConnectionException("connection refused"));

		assertThat(healthService.isDatabaseUp()).isFalse();
	}

}
