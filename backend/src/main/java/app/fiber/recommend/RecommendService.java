package app.fiber.recommend;

import app.fiber.catalog.Exercise;
import app.fiber.catalog.ExerciseRepository;
import app.fiber.catalog.Region;
import app.fiber.catalog.RegionRepository;
import app.fiber.catalog.RegionRole;
import app.fiber.common.BadRequestException;
import app.fiber.common.ConflictException;
import app.fiber.common.NotFoundException;
import app.fiber.coverage.CoverageResult;
import app.fiber.coverage.CoverageService;
import app.fiber.coverage.RegionCoverage;
import app.fiber.progression.Suggestion;
import app.fiber.settings.SplitDay;
import app.fiber.settings.SplitDayRepository;
import app.fiber.settings.UserSettings;
import app.fiber.settings.UserSettingsRepository;
import app.fiber.workout.ExerciseHistoryService;
import app.fiber.workout.SessionStatus;
import app.fiber.workout.WorkoutSession;
import app.fiber.workout.WorkoutSessionRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads what the pure Recommender and Similarity need (split days, this week's coverage, recovery,
 * the exercises the user can do) and maps their answers to the API.
 *
 * "Exercises the user can do" = not retired, and every piece of equipment they need is in the
 * user's equipment list (bodyweight exercises always fit).
 */
@Service
public class RecommendService {

	private static final String NO_SPLIT = "Choose a split in settings first";

	private final SplitDayRepository splitDayRepository;
	private final WorkoutSessionRepository sessionRepository;
	private final ExerciseRepository exerciseRepository;
	private final RegionRepository regionRepository;
	private final UserSettingsRepository settingsRepository;
	private final CoverageService coverageService;
	private final ExerciseHistoryService historyService;

	public RecommendService(SplitDayRepository splitDayRepository, WorkoutSessionRepository sessionRepository,
			ExerciseRepository exerciseRepository, RegionRepository regionRepository,
			UserSettingsRepository settingsRepository, CoverageService coverageService,
			ExerciseHistoryService historyService) {
		this.splitDayRepository = splitDayRepository;
		this.sessionRepository = sessionRepository;
		this.exerciseRepository = exerciseRepository;
		this.regionRepository = regionRepository;
		this.settingsRepository = settingsRepository;
		this.coverageService = coverageService;
		this.historyService = historyService;
	}

	/** The next workout. 409 if the user has no split days yet (not onboarded). */
	@Transactional(readOnly = true)
	public RecommendationResponse next(UUID userId) {
		List<SplitDayOption> splitDays = splitDayRepository.findByUserIdOrderByPositionAsc(userId).stream()
			.map(RecommendService::toOption)
			.toList();
		if (splitDays.isEmpty()) {
			throw new ConflictException(NO_SPLIT);
		}
		UUID lastSplitDayId = sessionRepository
			.findFirstByUserIdAndStatusOrderByFinishedAtDesc(userId, SessionStatus.FINISHED)
			.map(WorkoutSession::getSplitDayId)
			.orElse(null);
		CoverageResult week = coverageService.currentWeek(userId);
		Map<String, Exercise> usable = usableExercises(userId);

		Recommendation recommendation = Recommender.recommend(splitDays, lastSplitDayId, targets(week),
			effective(week), coverageService.recoveringRegions(userId), candidates(usable));

		List<Exercise> picked = recommendation.exerciseIds().stream().map(usable::get).toList();
		Map<String, Suggestion> suggestions = historyService.suggestionsFor(userId, picked);
		List<RecommendedExerciseResponse> exercises = picked.stream()
			.map(exercise -> toResponse(exercise, suggestions.get(exercise.getId())))
			.toList();
		return new RecommendationResponse(recommendation.day().id(), recommendation.day().name(), exercises,
			recommendation.planned());
	}

	/**
	 * Up to 5 exercises that could replace {@code exerciseId}: the same rules as the recommendation
	 * for that split day (or every region without a split day), most similar first.
	 */
	@Transactional(readOnly = true)
	public List<AlternativeResponse> alternatives(UUID userId, String exerciseId, UUID splitDayId) {
		Exercise current = exerciseRepository.findWithRegionsByIdIn(List.of(exerciseId)).stream()
			.findFirst()
			.orElseThrow(() -> new BadRequestException("Invalid input", Map.of("exerciseId", "unknown exercise")));
		Set<String> allowed = splitDayId == null ? allRegionIds() : findOwnSplitDay(userId, splitDayId).getRegionIds();
		Map<String, Exercise> usable = usableExercises(userId);

		List<Candidate> candidates = Recommender.candidates(allowed, coverageService.recoveringRegions(userId),
			candidates(usable));
		return Similarity.alternatives(toCandidate(current), candidates).stream()
			.map(alternative -> toResponse(usable.get(alternative.exerciseId()), alternative.similarity()))
			.toList();
	}

	// ---- loading ----

	/** Exercises the user can do, by id: not retired and all their equipment available. */
	private Map<String, Exercise> usableExercises(UUID userId) {
		Set<String> equipment = settingsRepository.findById(userId)
			.map(UserSettings::getEquipmentIds)
			.orElseThrow(() -> new NotFoundException("Settings not found"));
		return exerciseRepository.findAllWithLinks().stream()
			.filter(exercise -> !exercise.isRetired() && exercise.canBeDoneWith(equipment))
			.collect(Collectors.toMap(Exercise::getId, exercise -> exercise, (a, b) -> a, LinkedHashMap::new));
	}

	private Set<String> allRegionIds() {
		return regionRepository.findAll().stream().map(Region::getId).collect(Collectors.toSet());
	}

	/** A split day of another user is treated like an unknown one. */
	private SplitDay findOwnSplitDay(UUID userId, UUID splitDayId) {
		return splitDayRepository.findById(splitDayId)
			.filter(day -> day.getUserId().equals(userId))
			.orElseThrow(() -> new BadRequestException("Invalid input", Map.of("splitDayId", "unknown split day")));
	}

	private static Map<String, Integer> targets(CoverageResult week) {
		return week.regions().stream().collect(Collectors.toMap(RegionCoverage::regionId, RegionCoverage::target));
	}

	private static Map<String, Double> effective(CoverageResult week) {
		return week.regions().stream().collect(Collectors.toMap(RegionCoverage::regionId, RegionCoverage::effective));
	}

	// ---- mapping ----

	private static SplitDayOption toOption(SplitDay day) {
		return new SplitDayOption(day.getId(), day.getPosition(), day.getName(), Set.copyOf(day.getRegionIds()));
	}

	private static List<Candidate> candidates(Map<String, Exercise> exercises) {
		return exercises.values().stream().map(RecommendService::toCandidate).toList();
	}

	private static Candidate toCandidate(Exercise exercise) {
		return new Candidate(exercise.getId(), exercise.getPattern(), exercise.regionIds(RegionRole.PRIMARY),
			exercise.regionIds(RegionRole.SECONDARY));
	}

	private static RecommendedExerciseResponse toResponse(Exercise exercise, Suggestion suggestion) {
		return new RecommendedExerciseResponse(exercise.getId(), exercise.getName(),
			exercise.regionIds(RegionRole.PRIMARY), exercise.regionIds(RegionRole.SECONDARY),
			Recommender.SETS_PER_EXERCISE, List.of(exercise.getRepLow(), exercise.getRepHigh()),
			suggestion.weightKg(), suggestion.reps(), suggestion.reason());
	}

	private static AlternativeResponse toResponse(Exercise exercise, double similarity) {
		return new AlternativeResponse(exercise.getId(), exercise.getName(), exercise.regionIds(RegionRole.PRIMARY),
			exercise.regionIds(RegionRole.SECONDARY), similarity);
	}

}
