package app.fiber.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

	/** Finds an account ignoring case. Written as lower(email) so it uses the unique lower(email) index. */
	@Query("select u from UserAccount u where lower(u.email) = lower(:email)")
	Optional<UserAccount> findByEmailIgnoringCase(String email);

}
