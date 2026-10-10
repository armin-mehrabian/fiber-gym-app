package app.fiber.auth;

import java.io.Serial;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.userdetails.User;

/**
 * The logged-in user, as Spring Security keeps it in the session.
 * It is a normal Spring Security User (username = email) plus our account id,
 * so controllers can scope every query to {@link #getId()}.
 * Fiber has no roles, so the list of authorities is empty.
 */
public class AccountUser extends User {

	@Serial
	private static final long serialVersionUID = 1L;

	private final UUID id;

	public AccountUser(UserAccount account) {
		super(account.getEmail(), account.getPasswordHash(), List.of());
		this.id = account.getId();
	}

	public UUID getId() {
		return id;
	}

}
