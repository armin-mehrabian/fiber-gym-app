package app.fiber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full app against a real PostgreSQL 16 (Testcontainers, needs Docker).
 * Proves the context starts, Flyway applies V1, and the health check sees the database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FiberApplicationIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void healthReportsDatabaseUp() throws Exception {
		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ok"))
			.andExpect(jsonPath("$.database").value("up"));
	}

	@Test
	void flywayAppliedV1() {
		List<String> versions = jdbcTemplate.queryForList(
			"select version from flyway_schema_history where success", String.class);

		assertThat(versions).contains("1");
	}

	@Test
	void schemaHasAllTables() {
		List<String> tables = jdbcTemplate.queryForList(
			"select table_name from information_schema.tables where table_schema = 'public'", String.class);

		assertThat(tables).contains(
			"region", "equipment", "exercise", "exercise_equipment", "exercise_region",
			"user_account", "user_settings", "user_equipment", "region_target",
			"split_day", "split_day_region", "workout_session", "session_exercise", "workout_set");
	}

	// We use a unique index on lower(email) instead of the citext extension.
	@Test
	@Transactional // rolled back after the test, so no user rows are left behind
	void emailIsUniqueIgnoringCase() {
		jdbcTemplate.update("insert into user_account (email, password_hash) values ('a@fiber.app', 'x')");

		assertThatThrownBy(() -> jdbcTemplate.update(
				"insert into user_account (email, password_hash) values ('A@Fiber.app', 'y')"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	// One example of the CHECK constraints in V1: a rep range must not be upside down.
	@Test
	@Transactional // rolled back after the test
	void sessionExerciseWithRepLowAboveRepHighIsRejected() {
		jdbcTemplate.update("""
			insert into exercise (id, name, pattern, rep_low, rep_high)
			values ('test_row', 'Test row', 'row', 8, 12)""");
		UUID userId = jdbcTemplate.queryForObject(
			"insert into user_account (email, password_hash) values ('b@fiber.app', 'x') returning id", UUID.class);
		UUID sessionId = jdbcTemplate.queryForObject(
			"insert into workout_session (user_id, name) values (?, 'Pull') returning id", UUID.class, userId);

		assertThatThrownBy(() -> jdbcTemplate.update("""
				insert into session_exercise (session_id, position, exercise_id, target_sets, rep_low, rep_high)
				values (?, 1, 'test_row', 3, 12, 8)""", sessionId))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
