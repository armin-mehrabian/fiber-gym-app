package app.fiber.health;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Checks whether the database answers.
 */
@Service
public class HealthService {

	private final JdbcTemplate jdbcTemplate;

	public HealthService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * Runs the cheapest possible query. Any database error (no connection,
	 * timeout, ...) means "down"; Spring wraps them all in DataAccessException.
	 */
	public boolean isDatabaseUp() {
		try {
			jdbcTemplate.queryForObject("SELECT 1", Integer.class);
			return true;
		} catch (DataAccessException e) {
			return false;
		}
	}

}
