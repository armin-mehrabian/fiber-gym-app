package app.fiber.workout;

import app.fiber.auth.AccountUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Workout logging and history. Login required (see SecurityConfig).
 * The user always comes from the session; ids in the path only ever find that user's own data.
 */
@RestController
@RequestMapping("/api")
public class WorkoutController {

	private final WorkoutService workoutService;

	public WorkoutController(WorkoutService workoutService) {
		this.workoutService = workoutService;
	}

	/** 200 with the workout in progress, or 204 if there is none. */
	@GetMapping("/sessions/active")
	public ResponseEntity<SessionResponse> active(@AuthenticationPrincipal AccountUser user) {
		return workoutService.findActive(user.getId())
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

	/** The body is optional: no body (or no splitDayId) starts a workout called "Workout". */
	@PostMapping("/sessions")
	@ResponseStatus(HttpStatus.CREATED)
	public SessionResponse start(@AuthenticationPrincipal AccountUser user,
			@RequestBody(required = false) StartSessionRequest body) {
		return workoutService.start(user.getId(), body == null ? null : body.splitDayId());
	}

	@GetMapping("/sessions/{id}")
	public SessionResponse session(@AuthenticationPrincipal AccountUser user, @PathVariable UUID id) {
		return workoutService.get(user.getId(), id);
	}

	@PostMapping("/sessions/{id}/exercises")
	public SessionResponse addExercise(@AuthenticationPrincipal AccountUser user, @PathVariable UUID id,
			@Valid @RequestBody AddExerciseRequest body) {
		return workoutService.addExercise(user.getId(), id, body.exerciseId());
	}

	@DeleteMapping("/sessions/{id}/exercises/{position}")
	public SessionResponse removeExercise(@AuthenticationPrincipal AccountUser user, @PathVariable UUID id,
			@PathVariable int position) {
		return workoutService.removeExercise(user.getId(), id, position);
	}

	@PostMapping("/sessions/{id}/exercises/{position}/sets")
	@ResponseStatus(HttpStatus.CREATED)
	public SetResponse addSet(@AuthenticationPrincipal AccountUser user, @PathVariable UUID id,
			@PathVariable int position, @Valid @RequestBody NewSetRequest body) {
		return workoutService.addSet(user.getId(), id, position, body);
	}

	@PatchMapping("/sets/{setId}")
	public SetResponse updateSet(@AuthenticationPrincipal AccountUser user, @PathVariable UUID setId,
			@Valid @RequestBody SetUpdateRequest body) {
		return workoutService.updateSet(user.getId(), setId, body);
	}

	@DeleteMapping("/sets/{setId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteSet(@AuthenticationPrincipal AccountUser user, @PathVariable UUID setId) {
		workoutService.deleteSet(user.getId(), setId);
	}

	@PostMapping("/sessions/{id}/finish")
	public SessionResponse finish(@AuthenticationPrincipal AccountUser user, @PathVariable UUID id) {
		return workoutService.finish(user.getId(), id);
	}

	/** Discard: deletes a workout in progress. */
	@DeleteMapping("/sessions/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void discard(@AuthenticationPrincipal AccountUser user, @PathVariable UUID id) {
		workoutService.discard(user.getId(), id);
	}

	/** ?weeks= 1-52 (default 12): this week plus the weeks before it. */
	@GetMapping("/history")
	public List<HistoryWeek> history(@AuthenticationPrincipal AccountUser user,
			@RequestParam(defaultValue = "12") int weeks) {
		return workoutService.history(user.getId(), weeks);
	}

}
