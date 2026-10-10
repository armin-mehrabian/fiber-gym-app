package app.fiber.catalog;

import app.fiber.common.NotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

	public CatalogService(RegionRepository regionRepository, EquipmentRepository equipmentRepository,
			ExerciseRepository exerciseRepository) {
		this.regionRepository = regionRepository;
		this.equipmentRepository = equipmentRepository;
		this.exerciseRepository = exerciseRepository;
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
		String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
		// The catalog is small (about 80 exercises), so we load it all and filter and sort in Java.
		// That is simpler than one database query per filter combination, and sorting in Java
		// doesn't depend on the database's language settings.
		return exerciseRepository.findAllWithLinks().stream()
			.filter(exercise -> includeRetired || !exercise.isRetired())
			.filter(exercise -> exercise.getName().toLowerCase(Locale.ROOT).contains(needle))
			.sorted(BY_NAME)
			.map(ExerciseResponse::from)
			.toList();
	}

	/** One exercise, retired or not (old workouts still link to retired ones). */
	public ExerciseDetailResponse getExercise(String id) {
		return exerciseRepository.findById(id)
			.map(ExerciseDetailResponse::from)
			.orElseThrow(() -> new NotFoundException("Exercise not found: " + id));
	}

}
