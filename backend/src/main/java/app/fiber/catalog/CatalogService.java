package app.fiber.catalog;

import app.fiber.common.NotFoundException;
import app.fiber.settings.UserSettings;
import app.fiber.settings.UserSettingsRepository;
import app.fiber.workout.ExerciseHistoryResponse;
import app.fiber.workout.ExerciseHistoryService;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the exercise catalog (regions, equipment and exercises) and turns it into API responses.
 */
@Service
@Transactional(readOnly = true)
public class CatalogService {

	private static final Comparator<Exercise> BY_NAME =
		Comparator.comparing(Exercise::getName, String.CASE_INSENSITIVE_ORDER);

	private final RegionRepository regionRepository;
	private final EquipmentRepository equipmentRepository;
	private final ExerciseRepository exerciseRepository;
	private final UserSettingsRepository settingsRepository;
	private final ExerciseHistoryService historyService;

	public CatalogService(RegionRepository regionRepository, EquipmentRepository equipmentRepository,
			ExerciseRepository exerciseRepository, UserSettingsRepository settingsRepository,
			ExerciseHistoryService historyService) {
		this.regionRepository = regionRepository;
		this.equipmentRepository = equipmentRepository;
		this.exerciseRepository = exerciseRepository;
		this.settingsRepository = settingsRepository;
		this.historyService = historyService;
	}

	/** All regions, in body-figure order. */
	public List<RegionResponse> listRegions() {
		return regionRepository.findAllByOrderBySortOrderAsc().stream()
			.map(RegionResponse::from)
			.toList();
	}

	/** All equipment, in the same order as the seed file equipment.json. */
	public List<EquipmentResponse> listEquipment() {
		return equipmentRepository.findAllByOrderByPositionAscIdAsc().stream()
			.map(EquipmentResponse::from)
			.toList();
	}

	/**
	 * Exercises sorted by name.
	 *
	 * @param query          optional; keeps exercises whose name contains it, ignoring case
	 * @param includeRetired false hides retired exercises
	 */
	public List<ExerciseResponse> listExercises(String query, boolean includeRetired) {
		return listExercises(query, includeRetired, exercise -> true);
	}

	/**
	 * Like {@link #listExercises(String, boolean)}, but only exercises the user can do with their
	 * equipment: everything an exercise needs must be in the user's list. Bodyweight exercises always fit.
	 */
	public List<ExerciseResponse> listExercisesForUser(UUID userId, String query, boolean includeRetired) {
		Set<String> userEquipment = settingsRepository.findById(userId)
			.map(UserSettings::getEquipmentIds)
			.orElseThrow(() -> new NotFoundException("Settings not found"));
		return listExercises(query, includeRetired, exercise -> exercise.canBeDoneWith(userEquipment));
	}

	private List<ExerciseResponse> listExercises(String query, boolean includeRetired, Predicate<Exercise> fits) {
		String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
		// The catalog is small (about 80 exercises), so we load it all and filter and sort in Java.
		// That is simpler than one database query per filter combination, and sorting in Java
		// doesn't depend on the database's language settings.
		return exerciseRepository.findAllWithLinks().stream()
			.filter(exercise -> includeRetired || !exercise.isRetired())
			.filter(exercise -> exercise.getName().toLowerCase(Locale.ROOT).contains(needle))
			.filter(fits)
			.sorted(BY_NAME)
			.map(ExerciseResponse::from)
			.toList();
	}

	/**
	 * One exercise, retired or not (old workouts still link to retired ones).
	 *
	 * @param userId the logged-in user, whose best set and last time are filled in; null for a visitor
	 */
	public ExerciseDetailResponse getExercise(String id, UUID userId) {
		Exercise exercise = findExercise(id);
		if (userId == null) {
			return ExerciseDetailResponse.from(exercise, null, null);
		}
		return ExerciseDetailResponse.from(exercise, historyService.best(userId, id),
			historyService.lastTime(userId, id));
	}

	/** The user's finished workouts with this exercise, newest first (at most 20). 404 for an unknown id. */
	public List<ExerciseHistoryResponse> exerciseHistory(String id, UUID userId) {
		findExercise(id);
		return historyService.history(userId, id);
	}

	private Exercise findExercise(String id) {
		return exerciseRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Exercise not found: " + id));
	}

}
