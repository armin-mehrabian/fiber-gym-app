package app.fiber.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.fiber.common.ConflictException;
import app.fiber.settings.SettingsService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Signup rules that are hard to reach over HTTP. Repositories are mocked. */
class AuthServiceTest {

	private final UserAccountRepository accountRepository = mock(UserAccountRepository.class);
	private final SettingsService settingsService = mock(SettingsService.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final AuthService authService = new AuthService(accountRepository, settingsService, passwordEncoder,
		mock(AuthenticationManager.class));

	// Two signups with the same email at the same moment both pass the "already registered?" check;
	// the database's unique index rejects the second one, and that must still be a 409.
	@Test
	void signupRaceOnTheUniqueEmailIndexIsA409() {
		when(accountRepository.findByEmailIgnoringCase("race@fiber.app")).thenReturn(Optional.empty());
		when(passwordEncoder.encode(any())).thenReturn("hash");
		when(accountRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

		assertThatThrownBy(() -> authService.signup(
				new SignupRequest("Race@Fiber.app", "correct horse", "Europe/Berlin")))
			.isInstanceOfSatisfying(ConflictException.class, e -> {
				assertThat(e.getMessage()).isEqualTo("Email already registered");
				assertThat(e.getFields()).isEqualTo(Map.of("email", "already used"));
			});
		verifyNoInteractions(settingsService);
	}

}
