package app.fiber.workout;

import app.fiber.catalog.Exercise;
import app.fiber.common.NotFoundException;
import app.fiber.progression.LoggedSet;
import app.fiber.progression.PersonalBests;
import app.fiber.progression.ProgressionRule;
import app.fiber.progression.Suggestion;
import app.fiber.settings.UserSettings;
import app.fiber.settings.UserSettingsRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the user did with an exercise before: weight suggestions, "last time", best set and the
 * history list. Only the user's own FINISHED workouts count, and only completed sets.
 *
 * "Last time" is the newest finished workout in which the exercise has at least one completed
 * working set, so a workout where it was added but never done doesn't hide the real last time.
 * Dates are the day the workout started, in the user's timezone.
 */
@Service
@Transactional(readOnly = true)
public class ExerciseHistoryService {

	/** The history list shows at most this many workouts. */
	static final int MAX_HISTORY_WORKOUTS = 20;

	private final WorkoutSetRepository setRepository;
	private final UserSettingsRepository settingsRepository;

	public ExerciseHistoryService(WorkoutSetRepository setRepository, UserSettingsRepository settingsRepository) {
		this.setRepository = setRepository;
		this.settingsRepository = settingsRepository;
	}

	/** The suggestion for doing this exercise next, from the last time it was done. */
	public Suggestion suggestionFor(UUID userId, Exercise exercise) {
		return suggestionsFor(userId, List.of(exercise)).get(exercise.getId());
	}

	/** One suggestion per exercise id, loaded with one query for all of them. */
	public Map<String, Suggestion> suggestionsFor(UUID userId, Collection<Exercise> exercises) {
		Map<String, List<PastWorkout>> past = pastWorkouts(userId, exercises.stream().map(Exercise::getId).toList());
		Map<String, Suggestion> suggestions = new LinkedHashMap<>();
		for (Exercise exercise : exercises) {
			List<LoggedSet> lastSets = lastWorkout(past.getOrDefault(exercise.getId(), List.of()))
				.map(PastWorkout::workingSets)
				.orElse(List.of());
			suggestions.put(exercise.getId(),
				ProgressionRule.suggest(exercise.getPattern(), exercise.getRepLow(), exercise.getRepHigh(), lastSets));
		}
		return suggestions;
	}

	/** "Last time" per exercise id; exercises never done are left out of the map. */
	public Map<String, LastTimeResponse> lastTimes(UUID userId, Collection<String> exerciseIds) {
		ZoneId zone = userTimezone(userId);
		Map<String, LastTimeResponse> lastTimes = new LinkedHashMap<>();
		pastWorkouts(userId, exerciseIds).forEach((exerciseId, workouts) ->
			lastWorkout(workouts).ifPresent(last -> lastTimes.put(exerciseId, toLastTime(last, zone))));
		return lastTimes;
	}

	/** "Last time" for one exercise, or null if it was never done. */
	public LastTimeResponse lastTime(UUID userId, String exerciseId) {
		return lastTimes(userId, List.of(exerciseId)).get(exerciseId);
	}

	/**
	 * The best working set ever (highest estimated one-rep max), or null if there is none.
	 * When two sets are equally good, the more recent one is shown.
	 */
	public BestSetResponse best(UUID userId, String exerciseId) {
		List<FinishedSetRow> workingSets = rows(userId, List.of(exerciseId)).stream()
			.filter(row -> !row.warmup())
			.toList();
		ZoneId zone = userTimezone(userId);
		return PersonalBests.best(workingSets, FinishedSetRow::toLoggedSet)
			.map(row -> new BestSetResponse(row.weightKg(), row.reps(), day(row.sessionStartedAt(), zone)))
			.orElse(null);
	}

	/** The newest 20 finished workouts with completed sets of this exercise, newest first. */
	public List<ExerciseHistoryResponse> history(UUID userId, String exerciseId) {
		ZoneId zone = userTimezone(userId);
		return PastWorkout.groupBySession(rows(userId, List.of(exerciseId))).stream()
			.limit(MAX_HISTORY_WORKOUTS)
			.map(workout -> new ExerciseHistoryResponse(workout.sessionId(), day(workout.startedAt(), zone),
				workout.sets().stream()
					.map(set -> new HistorySetResponse(set.weightKg(), set.reps(), set.warmup()))
					.toList()))
			.toList();
	}

	/**
	 * Per exercise id: the working sets of all finished workouts that started before {@code before}.
	 * Used to decide personal bests ("better than every earlier workout").
	 */
	public Map<String, List<LoggedSet>> workingSetsBefore(UUID userId, Collection<String> exerciseIds, Instant before) {
		return rows(userId, exerciseIds).stream()
			.filter(row -> !row.warmup() && row.sessionStartedAt().isBefore(before))
			.collect(Collectors.groupingBy(FinishedSetRow::exerciseId,
				Collectors.mapping(FinishedSetRow::toLoggedSet, Collectors.toList())));
	}

	// ---- helpers ----

	/** Per exercise id, its finished workouts (newest first) with the completed sets in each. */
	private Map<String, List<PastWorkout>> pastWorkouts(UUID userId, Collection<String> exerciseIds) {
		Map<String, List<FinishedSetRow>> byExercise = rows(userId, exerciseIds).stream()
			.collect(Collectors.groupingBy(FinishedSetRow::exerciseId, LinkedHashMap::new, Collectors.toList()));
		Map<String, List<PastWorkout>> result = new LinkedHashMap<>();
		byExercise.forEach((exerciseId, rows) -> result.put(exerciseId, PastWorkout.groupBySession(rows)));
		return result;
	}

	private List<FinishedSetRow> rows(UUID userId, Collection<String> exerciseIds) {
		if (exerciseIds.isEmpty()) {
			return List.of();
		}
		return setRepository.findCompletedSetsInFinishedWorkouts(userId, exerciseIds);
	}

	/** The newest workout (the list is newest first) with at least one completed working set. */
	static Optional<PastWorkout> lastWorkout(List<PastWorkout> newestFirst) {
		return newestFirst.stream().filter(workout -> !workout.workingSets().isEmpty()).findFirst();
	}

	private static LastTimeResponse toLastTime(PastWorkout workout, ZoneId zone) {
		List<PastSetResponse> sets = workout.workingSets().stream()
			.map(set -> new PastSetResponse(set.weightKg(), set.reps()))
			.toList();
		return new LastTimeResponse(day(workout.startedAt(), zone), sets);
	}

	private static LocalDate day(Instant moment, ZoneId zone) {
		return moment.atZone(zone).toLocalDate();
	}

	private ZoneId userTimezone(UUID userId) {
		return settingsRepository.findById(userId)
			.map(UserSettings::getTimezone)
			.map(ZoneId::of)
			.orElseThrow(() -> new NotFoundException("Settings not found"));
	}

}
