package app.fiber.auth;

import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Tells Spring Security how to find an account at login: by email, ignoring case.
 * Having this bean also stops Spring Boot from creating its demo in-memory user.
 */
@Service
public class AccountUserDetailsService implements UserDetailsService {

	private final UserAccountRepository accountRepository;

	public AccountUserDetailsService(UserAccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) {
		// Spring Security turns this exception into the same "bad credentials" error as a wrong password.
		return accountRepository.findByEmailIgnoringCase(email.strip().toLowerCase(Locale.ROOT))
			.map(AccountUser::new)
			.orElseThrow(() -> new UsernameNotFoundException("No account for this email"));
	}

}
