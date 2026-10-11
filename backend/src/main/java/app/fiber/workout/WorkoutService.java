package app.fiber.workout;

import app.fiber.catalog.Exercise;
import app.fiber.catalog.ExerciseRepository;
import app.fiber.catalog.RegionRole;
import app.fiber.common.BadRequestException;
import app.fiber.common.ConflictException;
import app.fiber.common.NotFoundException;
import app.fiber.progression.Suggestion;
import app.fiber.settings.SplitDay;
import app.fiber.settings.SplitDayRepository;
import app.fiber.settings.UserSettings;
import app.fiber.settings.UserSettingsRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logging workouts: start, add exercises and sets, finish or discard, and the history list.
 *
 * Every method takes the logged-in user's id and only finds that user's workouts. Someone else's
 * workout or set is "not found" (404), so nobody can even tell whether it exists.
 * A finished workout is read-only: any change answers 409.
 */
@Service
public class WorkoutService {

	static final String DEFAULT_NAME = "Workout";
	/** How many sets a newly added exercise is planned with. */
	static final int DEFAULT_TARGET_SETS = 3;
	static final int MAX_HISTORY_WEEKS = 52;

	/** The database index that allows only one ACTIVE workout per user. */
	static final String ONE_ACTIVE_INDEX = "workout_session_one_active_per_user";

	private static final String ALREADY_ACTIVE = "A workout is already in progress";
	private static final String FINISHED = "This workout is finished";
	private static final String SWAP_WITH_SETS = "Remove its sets before swapping";

	private final WorkoutSessionRepository sessionRepository;
	private final WorkoutSetRepository setRepository;
	private final ExerciseRepository exerciseRepository;
	private final SplitDayRepository splitDayRepository;
	private final UserSettingsRepository settingsRepository;
	private final ExerciseHistoryService historyService;

	public WorkoutService(WorkoutSessionRepository sessionRepository, WorkoutSetRepository setRepository,
			ExerciseRepository exerciseRepository, SplitDayRepository splitDayRepository,
			UserSettingsRepository settingsRepository, ExerciseHistoryService historyService) {
		this.sessionRepository = sessionRepository;
		this.setRepository = setRepository;
		this.exerciseRepository = exerciseRepository;
		this.splitDayRepository = splitDayRepository;
		this.settingsRepository = settingsRepository;
		this.historyService = historyService;
	}

	// ---- sessions ----

	/** The user's workout in progress, if there is one. */
	@Transactional(readOnly = true)
	public Optional<SessionResponse> findActive(UUID userId) {
		return sessionRepository.findByUserIdAndStatus(userId, SessionStatus.ACTIVE).map(this::toResponse);
	}

	/**
	 * Starts a workout. It is named after the split day if one is given, otherwise "Workout".
	 * The given exercises (may be empty) are added in order, each with its suggestion, like
	 * {@link #addExercise}. Only one workout can be in progress per user.
	 */
	@Transactional
	public SessionResponse start(UUID userId, UUID splitDayId, List<String> exerciseIds) {
		if (sessionRepository.existsByUserIdAndStatus(userId, SessionStatus.ACTIVE)) {
			throw new ConflictException(ALREADY_ACTIVE);
		}
		String name = splitDayId == null ? DEFAULT_NAME : findOwnSplitDay(userId, splitDayId).getName();
		List<Exercise> exercises = findLibraryExercises(exerciseIds);
		WorkoutSession session = new WorkoutSession(userId, splitDayId, name, now());
		Map<String, Suggestion> suggestions = historyService.suggestionsFor(userId, exercises);
		for (Exercise exercise : exercises) {
			addPlanned(session, exercise, suggestions.get(exercise.getId()));
		}
		try {
			sessionRepository.saveAndFlush(session);
		} catch (DataIntegrityViolationException e) {
			// Two starts at the same moment both passed the check above, and the database index
			// stopped the second one. Any other database error is a real bug and is not hidden.
			if (isSecondActiveSession(e)) {
				throw new ConflictException(ALREADY_ACTIVE);
			}
			throw e;
		}
		return toResponse(session);
	}

	/** True if the error is the "only one active workout per user" index, and nothing else. */
	static boolean isSecondActiveSession(DataIntegrityViolationException e) {
		return e.getCause() instanceof ConstraintViolationException violation
			&& ONE_ACTIVE_INDEX.equals(violation.getConstraintName());
	}

	@Transactional(readOnly = true)
	public SessionResponse get(UUID userId, UUID sessionId) {
		return toResponse(findOwnSession(userId, sessionId));
	}

	/** Marks the workout as done. Allowed even with no completed sets. */
	@Transactional
	public SessionResponse finish(UUID userId, UUID sessionId) {
		WorkoutSession session = findOwnActiveSession(userId, sessionId);
		session.finish(now());
		return toResponse(session);
	}

	/** Deletes a workout in progress with all its exercises and sets. Finished workouts can't be discarded. */
	@Transactional
	public void discard(UUID userId, UUID sessionId) {
		sessionRepository.delete(findOwnActiveSession(userId, sessionId));
	}

	// ---- exercises in a session ----

	/**
	 * Adds an exercise at the end of the workout, with 3 planned sets, the exercise's rep range and
	 * a weight suggestion from the last time the user did it.
	 */
	@Transactional
	public SessionResponse addExercise(UUID userId, UUID sessionId, String exerciseId) {
		WorkoutSession session = findOwnActiveSession(userId, sessionId);
		Exercise exercise = findLibraryExercise(exerciseId);
		addPlanned(session, exercise, historyService.suggestionFor(userId, exercise));
		sessionRepository.flush();
		return toResponse(session);
	}

	private static void addPlanned(WorkoutSession session, Exercise exercise, Suggestion suggestion) {
		session.addExercise(exercise.getId(), DEFAULT_TARGET_SETS, exercise.getRepLow(), exercise.getRepHigh())
			.applySuggestion(suggestion);
	}

	/**
	 * Swap: another exercise takes this position, with its own rep range and suggestion.
	 * Only while the exercise has no sets at all (409 otherwise), so no logged work is ever lost.
	 */
	@Transactional
	public SessionResponse swapExercise(UUID userId, UUID sessionId, int position, String exerciseId) {
		WorkoutSession session = findOwnActiveSession(userId, sessionId);
		SessionExercise slot = findExercise(session, position);
		if (!slot.getSets().isEmpty()) {
			throw new ConflictException(SWAP_WITH_SETS);
		}
		Exercise exercise = findLibraryExercise(exerciseId);
		slot.swapTo(exercise.getId(), exercise.getRepLow(), exercise.getRepHigh());
		slot.applySuggestion(historyService.suggestionFor(userId, exercise));
		sessionRepository.flush();
		return toResponse(session);
	}

	/** Removes the exercise at this position, with its sets. The exercises after it move up by one. */
	@Transactional
	public SessionResponse removeExercise(UUID userId, UUID sessionId, int position) {
		WorkoutSession session = findOwnActiveSession(userId, sessionId);
		SessionExercise removed = findExercise(session, position);
		session.removeExercise(removed);
		// Positions are unique within a workout, so the removed row has to be gone from the
		// database before the next exercise can take its position.
		sessionRepository.flush();
		for (SessionExercise later : session.getExercises()) {
			if (later.getPosition() > position) {
				later.setPosition(later.getPosition() - 1);
				// One at a time, front to back, so two exercises never share a position, even briefly.
				sessionRepository.flush();
			}
		}
		return toResponse(session);
	}

	// ---- sets ----

	/** Logs a new set for the exercise at this position: the next set number, not completed yet. */
	@Transactional
	public SetResponse addSet(UUID userId, UUID sessionId, int position, NewSetRequest request) {
		WorkoutSession session = findOwnActiveSession(userId, sessionId);
		SessionExercise exercise = findExercise(session, position);
		boolean warmup = Boolean.TRUE.equals(request.isWarmup());
		WorkoutSet set = exercise.addSet(request.weightKg(), request.reps(), warmup, now());
		setRepository.save(set);
		return SetResponse.from(set);
	}

	/** Changes only the fields that were sent. */
	@Transactional
	public SetResponse updateSet(UUID userId, UUID setId, SetUpdateRequest request) {
		WorkoutSet set = findOwnChangeableSet(userId, setId);
		if (request.weightKg() != null) {
			set.setWeightKg(request.weightKg());
		}
		if (request.reps() != null) {
			set.setReps(request.reps());
		}
		if (request.isWarmup() != null) {
			set.setWarmup(request.isWarmup());
		}
		if (request.completed() != null) {
			set.setCompleted(request.completed());
		}
		return SetResponse.from(set);
	}

	/** Deletes a set. The other sets of that exercise are numbered 1..n again. */
	@Transactional
	public void deleteSet(UUID userId, UUID setId) {
		WorkoutSet set = findOwnChangeableSet(userId, setId);
		set.getSessionExercise().removeSet(set);
	}

	// ---- history ----

	/**
	 * The user's finished workouts of the last {@code weeks} weeks (this week included),
	 * grouped into Monday-Sunday weeks in the user's timezone.
	 */
	@Transactional(readOnly = true)
	public List<HistoryWeek> history(UUID userId, int weeks) {
		if (weeks < 1 || weeks > MAX_HISTORY_WEEKS) {
			throw new BadRequestException("Invalid input",
				Map.of("weeks", "must be between 1 and " + MAX_HISTORY_WEEKS));
		}
		ZoneId zone = userTimezone(userId);
		Instant from = WeekMath.historyStart(Instant.now(), zone, weeks);
		return WeekMath.groupByWeek(sessionRepository.findFinishedSince(userId, from), zone);
	}

	// ---- lookups, always scoped to the user ----

	/** For reading only; changes go through findOwnActiveSession, which also locks the workout. */
	private WorkoutSession findOwnSession(UUID userId, UUID sessionId) {
		return sessionRepository.findByIdAndUserId(sessionId, userId)
			.orElseThrow(() -> new NotFoundException("Workout not found"));
	}

	/**
	 * The user's workout, if it may still be changed (404 if not theirs, 409 if finished).
	 * The row stays locked until the transaction ends, so changes to one workout never overlap.
	 */
	private WorkoutSession findOwnActiveSession(UUID userId, UUID sessionId) {
		WorkoutSession session = sessionRepository.findByIdAndUserIdForUpdate(sessionId, userId)
			.orElseThrow(() -> new NotFoundException("Workout not found"));
		if (session.isFinished()) {
			throw new ConflictException(FINISHED);
		}
		return session;
	}

	/**
	 * The user's set, if its workout may still be changed (404 if not theirs, 409 if finished).
	 * The workout is locked first and the set is read after that, so the set is never an old copy
	 * from before another request renumbered or deleted it.
	 */
	private WorkoutSet findOwnChangeableSet(UUID userId, UUID setId) {
		UUID sessionId = setRepository.findSessionIdOwnedBy(setId, userId)
			.orElseThrow(() -> new NotFoundException("Set not found"));
		findOwnActiveSession(userId, sessionId);
		return setRepository.findById(setId)
			.orElseThrow(() -> new NotFoundException("Set not found"));
	}

	private static SessionExercise findExercise(WorkoutSession session, int position) {
		return session.exerciseAt(position)
			.orElseThrow(() -> new NotFoundException("No exercise at position " + position + " in this workout"));
	}

	private Exercise findLibraryExercise(String exerciseId) {
		return exerciseRepository.findById(exerciseId)
			.orElseThrow(() -> new BadRequestException("Invalid input", Map.of("exerciseId", "unknown exercise")));
	}

	/**
	 * The exercises of a new workout, in the order given. Unknown ids are named by their place in
	 * the list, e.g. {"exercises[1].exerciseId": "unknown exercise"}, and nothing is started.
	 */
	private List<Exercise> findLibraryExercises(List<String> exerciseIds) {
		Map<String, Exercise> found = exerciseRepository.findAllById(exerciseIds).stream()
			.collect(Collectors.toMap(Exercise::getId, Function.identity()));
		List<Exercise> exercises = new ArrayList<>();
		Map<String, String> unknown = new LinkedHashMap<>();
		for (int i = 0; i < exerciseIds.size(); i++) {
			Exercise exercise = found.get(exerciseIds.get(i));
			if (exercise == null) {
				unknown.put("exercises[" + i + "].exerciseId", "unknown exercise");
			}
			exercises.add(exercise);
		}
		if (!unknown.isEmpty()) {
			throw new BadRequestException("Invalid input", unknown);
		}
		return exercises;
	}

	/** A split day of another user is treated like an unknown one. */
	private SplitDay findOwnSplitDay(UUID userId, UUID splitDayId) {
		return splitDayRepository.findById(splitDayId)
			.filter(day -> day.getUserId().equals(userId))
			.orElseThrow(() -> new BadRequestException("Invalid input", Map.of("splitDayId", "unknown split day")));
	}

	private ZoneId userTimezone(UUID userId) {
		return settingsRepository.findById(userId)
			.map(UserSettings::getTimezone)
			.map(ZoneId::of)
			.orElseThrow(() -> new NotFoundException("Settings not found"));
	}

	/**
	 * The current time, cut to milliseconds. The database keeps microseconds and Java may have more,
	 * so without this a workout could show a slightly different time after it is saved and read back.
	 */
	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MILLIS);
	}

	// ---- mapping to the API ----

	private SessionResponse toResponse(WorkoutSession session) {
		// Name and regions come from the exercise library, loaded in one query for the whole workout.
		List<String> exerciseIds = session.getExercises().stream().map(SessionExercise::getExerciseId).toList();
		Map<String, Exercise> library = exerciseRepository.findWithRegionsByIdIn(exerciseIds).stream()
			.collect(Collectors.toMap(Exercise::getId, Function.identity()));
		// "Last time" helps while training; a finished workout doesn't need it.
		Map<String, LastTimeResponse> lastTimes = session.isFinished()
			? Map.of()
			: historyService.lastTimes(session.getUserId(), exerciseIds);
		List<SessionExerciseResponse> exercises = session.getExercises().stream()
			.map(exercise -> toResponse(exercise, library.get(exercise.getExerciseId()),
				lastTimes.get(exercise.getExerciseId())))
			.toList();
		return new SessionResponse(session.getId(), session.getName(), session.getSplitDayId(), session.getStatus(),
			session.getStartedAt(), session.getFinishedAt(), exercises);
	}

	private static SessionExerciseResponse toResponse(SessionExercise exercise, Exercise libraryEntry,
			LastTimeResponse lastTime) {
		List<SetResponse> sets = exercise.getSets().stream().map(SetResponse::from).toList();
		return new SessionExerciseResponse(
			exercise.getPosition(),
			exercise.getExerciseId(),
			libraryEntry.getName(),
			libraryEntry.regionIds(RegionRole.PRIMARY),
			libraryEntry.regionIds(RegionRole.SECONDARY),
			exercise.getTargetSets(),
			List.of(exercise.getRepLow(), exercise.getRepHigh()),
			exercise.getSuggestedWeightKg(),
			exercise.getSuggestedReps(),
			exercise.getSuggestionReason(),
			lastTime,
			sets);
	}

}
