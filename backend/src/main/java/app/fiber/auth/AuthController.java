package app.fiber.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signup, login, logout and "who am I".
 *
 * A login lives in the server-side HTTP session; the browser only holds the session cookie
 * (JSESSIONID). Every POST needs the CSRF token (see SecurityConfig), which the frontend gets
 * from GET /api/auth/csrf first.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

	// Saves the logged-in user into the HTTP session. The security filter chain reads it
	// back from there on every following request.
	private final SecurityContextRepository sessionContextRepository = new HttpSessionSecurityContextRepository();

	// Ends the session and forgets the logged-in user.
	private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	/** Makes sure the XSRF-TOKEN cookie exists. Public. */
	@GetMapping("/auth/csrf")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void csrf(CsrfToken csrfToken) {
		// Spring loads the token lazily. Reading it is what makes Spring write the cookie.
		csrfToken.getToken();
	}

	/** Creates the account and logs the new user in. */
	@PostMapping("/auth/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse signup(@Valid @RequestBody SignupRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		UserResponse user = authService.signup(body);
		// Log in exactly like a normal login does, now that the account exists.
		startSession(authService.authenticate(body.email(), body.password()), request, response);
		return user;
	}

	/** 401 "Wrong email or password" if the login fails (see ApiExceptionHandler). */
	@PostMapping("/auth/login")
	public UserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		Authentication authentication = authService.authenticate(body.email(), body.password());
		startSession(authentication, request, response);
		AccountUser user = (AccountUser) authentication.getPrincipal();
		return authService.currentUser(user.getId());
	}

	@PostMapping("/auth/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
		logoutHandler.logout(request, response, authentication);
	}

	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal AccountUser user) {
		return authService.currentUser(user.getId());
	}

	private void startSession(Authentication authentication, HttpServletRequest request,
			HttpServletResponse response) {
		// Give an existing session a new id at login, so an id someone planted before the
		// login can't be used to ride on it afterwards (session fixation).
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		sessionContextRepository.saveContext(context, request, response);
	}

}
