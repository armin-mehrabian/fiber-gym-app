package app.fiber.workout;

import app.fiber.catalog.Exercise;
import app.fiber.catalog.ExerciseRepository;
import app.fiber.catalog.RegionRole;
import app.fiber.common.ConflictException;
import app.fiber.common.NotFoundException;
import app.fiber.coverage.CoverageService;
import app.fiber.progression.LoggedSet;
import app.fiber.progression.PersonalBests;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The summary shown after finishing a workout: how long it took, how many working sets, which
 * regions it hit, how the week's coverage changed, and any new personal bests.
 *
 * Only working sets count here: completed and not warm-ups.
 */
@Service
public class SummaryService {

	private static final String NOT_FINISHED = "Finish the workout first";

	private final WorkoutSessionRepository sessionRepository;
	private final ExerciseRepository exerciseRepository;
	private final ExerciseHistoryService historyService;
	private final CoverageService coverageService;

	public SummaryService(WorkoutSessionRepository sessionRepository, ExerciseRepository exerciseRepository,
			ExerciseHistoryService historyService, CoverageService coverageService) {
		this.sessionRepository = sessionRepository;
		this.exerciseRepository = exerciseRepository;
		this.historyService = historyService;
		this.coverageService = coverageService;
	}

	/** 404 if it isn't the user's workout, 409 while it is still in progress. */
	@Transactional(readOnly = true)
	public SummaryResponse summary(UUID userId, UUID sessionId) {
		WorkoutSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
			.orElseThrow(() -> new NotFoundException("Workout not found"));
		if (!session.isFinished()) {
			throw new ConflictException(NOT_FINISHED);
		}
		Map<String, List<LoggedSet>> workingSets = workingSetsByExercise(session);
		Map<String, Exercise> library = exerciseRepository.findWithRegionsByIdIn(workingSets.keySet()).stream()
			.collect(Collectors.toMap(Exercise::getId, Function.identity()));
		int workingSetCount = workingSets.values().stream().mapToInt(List::size).sum();

		// "Before" = the same week with this workout's sets left out.
		Set<UUID> ownSetIds = session.getExercises().stream()
			.flatMap(exercise -> exercise.getSets().stream())
			.map(WorkoutSet::getId)
			.collect(Collectors.toSet());
		int before = coverageService.weekPercent(userId, session.getStartedAt(), ownSetIds);
		int after = coverageService.weekPercent(userId, session.getStartedAt(), Set.of());

		return new SummaryResponse(session.getId(), session.getName(),
			durationMinutes(session.getStartedAt(), session.getFinishedAt()), workingSetCount,
			regionsHit(library.values()), before, after,
			personalBests(userId, session.getStartedAt(), workingSets, library));
	}

	/** Finish time minus start time, rounded to whole minutes (30 seconds and up rounds up). */
	static long durationMinutes(Instant startedAt, Instant finishedAt) {
		return Math.round(Duration.between(startedAt, finishedAt).toSeconds() / 60.0);
	}

	/**
	 * The working sets of each exercise in the workout, in workout order. An exercise that appears
	 * twice is merged; exercises without any working set are left out.
	 */
	static Map<String, List<LoggedSet>> workingSetsByExercise(WorkoutSession session) {
		Map<String, List<LoggedSet>> byExercise = new LinkedHashMap<>();
		for (SessionExercise exercise : session.getExercises()) {
			for (WorkoutSet set : exercise.getSets()) {
				if (set.isCompleted() && !set.isWarmup()) {
					byExercise.computeIfAbsent(exercise.getExerciseId(), id -> new ArrayList<>())
						.add(new LoggedSet(set.getWeightKg(), set.getReps()));
				}
			}
		}
		return byExercise;
	}

	/** Region id to 1 if any trained exercise hits it mainly, else 0.5 if one helps it. Sorted by region id. */
	static Map<String, Double> regionsHit(Iterable<Exercise> trainedExercises) {
		Map<String, Double> hit = new TreeMap<>();
		for (Exercise exercise : trainedExercises) {
			exercise.regionIds(RegionRole.SECONDARY).forEach(region -> hit.merge(region, 0.5, Math::max));
			exercise.regionIds(RegionRole.PRIMARY).forEach(region -> hit.merge(region, 1.0, Math::max));
		}
		return hit;
	}

	/**
	 * Exercises whose best set in this workout beats their best set in every earlier finished workout.
	 * The first time an exercise is done is not a personal best.
	 */
	private List<PersonalBestResponse> personalBests(UUID userId, Instant startedAt,
			Map<String, List<LoggedSet>> workingSets, Map<String, Exercise> library) {
		Map<String, List<LoggedSet>> earlier = historyService.workingSetsBefore(userId, workingSets.keySet(), startedAt);
		List<PersonalBestResponse> bests = new ArrayList<>();
		workingSets.forEach((exerciseId, sets) -> {
			if (PersonalBests.isPersonalBest(sets, earlier.getOrDefault(exerciseId, List.of()))) {
				LoggedSet best = PersonalBests.best(sets, Function.identity()).orElseThrow();
				bests.add(new PersonalBestResponse(exerciseId, library.get(exerciseId).getName(), best.weightKg(),
					best.reps()));
			}
		});
		return bests;
	}

}
