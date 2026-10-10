package app.fiber.auth;

import app.fiber.common.BadRequestException;
import app.fiber.common.ConflictException;
import app.fiber.common.NotFoundException;
import app.fiber.settings.SettingsService;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accounts: signing up, checking a login, and reading the current user.
 * Keeping the login in the HTTP session is the controller's job.
 */
@Service
public class AuthService {

	/** BCrypt hashes at most 72 bytes. Letters like "é" take 2 bytes, so 72 characters can be too many. */
	private static final int MAX_PASSWORD_BYTES = 72;

	private final UserAccountRepository accountRepository;
	private final SettingsService settingsService;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;

	public AuthService(UserAccountRepository accountRepository, SettingsService settingsService,
			PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager) {
		this.accountRepository = accountRepository;
		this.settingsService = settingsService;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
	}

	/**
	 * Creates the account, its settings and its default weekly targets, all in one transaction:
	 * if any part fails, nothing is saved.
	 */
	@Transactional
	public UserResponse signup(SignupRequest request) {
		String email = normalizeEmail(request.email());
		if (request.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
			throw new BadRequestException("Invalid input", Map.of("password", "is too long"));
		}
		if (accountRepository.findByEmailIgnoringCase(email).isPresent()) {
			throw emailAlreadyRegistered();
		}

		UserAccount account;
		try {
			// Flush now, so the unique email index is checked here and not later at commit.
			account = accountRepository.saveAndFlush(new UserAccount(email, passwordEncoder.encode(request.password())));
		} catch (DataIntegrityViolationException e) {
			// Two signups with the same email at the same moment: both passed the check above,
			// and the database's unique index stopped the second one.
			throw emailAlreadyRegistered();
		}
		settingsService.createDefaults(account.getId(), request.timezone());
		return new UserResponse(account.getId(), account.getEmail(), false);
	}

	private static ConflictException emailAlreadyRegistered() {
		return new ConflictException("Email already registered", Map.of("email", "already used"));
	}

	/**
	 * Checks email and password with Spring Security.
	 * Throws BadCredentialsException for an unknown email or a wrong password (same error for both).
	 */
	public Authentication authenticate(String email, String password) {
		return authenticationManager.authenticate(
			UsernamePasswordAuthenticationToken.unauthenticated(normalizeEmail(email), password));
	}

	@Transactional(readOnly = true)
	public UserResponse currentUser(UUID userId) {
		UserAccount account = accountRepository.findById(userId)
			.orElseThrow(() -> new NotFoundException("Account not found"));
		return new UserResponse(account.getId(), account.getEmail(), settingsService.isOnboarded(userId));
	}

	/** Emails are stored and compared in lowercase, without surrounding spaces. */
	private static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

}
