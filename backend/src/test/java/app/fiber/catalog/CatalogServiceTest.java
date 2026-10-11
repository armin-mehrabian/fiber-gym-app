package app.fiber.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.fiber.common.NotFoundException;
import app.fiber.settings.UserSettings;
import app.fiber.settings.UserSettingsRepository;
import app.fiber.workout.ExerciseHistoryService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Plain unit test with mocked repositories: filtering, sorting and the entity-to-JSON mapping.
 */
class CatalogServiceTest {

	private final RegionRepository regionRepository = mock(RegionRepository.class);
	private final EquipmentRepository equipmentRepository = mock(EquipmentRepository.class);
	private final ExerciseRepository exerciseRepository = mock(ExerciseRepository.class);
	private final UserSettingsRepository settingsRepository = mock(UserSettingsRepository.class);
	private final CatalogService catalogService =
		new CatalogService(regionRepository, equipmentRepository, exerciseRepository, settingsRepository,
			mock(ExerciseHistoryService.class));

	@Test
	void equipmentKeepsTheRepositoryOrderAndMapsIdAndName() {
		when(equipmentRepository.findAllByOrderByPositionAscIdAsc()).thenReturn(List.of(
			new Equipment("cables", "Cable station", 0),
			new Equipment("barbell", "Barbell", 1)));

		assertThat(catalogService.listEquipment()).containsExactly(
			new EquipmentResponse("cables", "Cable station"),
			new EquipmentResponse("barbell", "Barbell"));
	}

	@Test
	void exercisesAreSortedByNameIgnoringCaseAndRetiredOnesAreHidden() {
		when(exerciseRepository.findAllWithLinks()).thenReturn(List.of(
			exercise("squat", "Squat", false),
			exercise("old_press", "Old press", true),
			exercise("bench_press", "bench press", false)));

		assertThat(catalogService.listExercises(null, false))
			.extracting(ExerciseResponse::id)
			.containsExactly("bench_press", "squat");
	}

	@Test
	void includeRetiredShowsRetiredExercises() {
		when(exerciseRepository.findAllWithLinks()).thenReturn(List.of(
			exercise("squat", "Squat", false),
			exercise("old_press", "Old press", true)));

		assertThat(catalogService.listExercises(null, true))
			.extracting(ExerciseResponse::id)
			.containsExactly("old_press", "squat");
	}

	@Test
	void queryMatchesPartOfTheNameIgnoringCaseAndSurroundingSpaces() {
		when(exerciseRepository.findAllWithLinks()).thenReturn(List.of(
			exercise("cable_fly", "Cable fly", false),
			exercise("single_arm_cable_row", "Single-arm cable row", false),
			exercise("squat", "Squat", false)));

		assertThat(catalogService.listExercises("  CaBLe ", false))
			.extracting(ExerciseResponse::id)
			.containsExactly("cable_fly", "single_arm_cable_row");
	}

	@Test
	void blankQueryReturnsEverything() {
		when(exerciseRepository.findAllWithLinks()).thenReturn(List.of(exercise("squat", "Squat", false)));

		assertThat(catalogService.listExercises("", false)).hasSize(1);
	}

	// ---- only exercises for the user's equipment (?mine=true) ----

	@Test
	void mineKeepsExercisesWhoseEquipmentTheUserHasAllOfPlusBodyweight() {
		UUID userId = givenUserWithEquipment("barbell", "cables");
		when(exerciseRepository.findAllWithLinks()).thenReturn(List.of(
			exercise("push_up", "Push-up", false, Set.of()),
			exercise("cable_fly", "Cable fly", false, Set.of("cables")),
			exercise("bench_press", "Bench press", false, Set.of("barbell", "bench")), // bench missing
			exercise("barbell_row", "Barbell row", false, Set.of("barbell"))));

		assertThat(catalogService.listExercisesForUser(userId, null, false))
			.extracting(ExerciseResponse::id)
			.containsExactly("barbell_row", "cable_fly", "push_up");
	}

	@Test
	void mineWithNoEquipmentLeavesOnlyBodyweightExercises() {
		UUID userId = givenUserWithEquipment();
		when(exerciseRepository.findAllWithLinks()).thenReturn(List.of(
			exercise("push_up", "Push-up", false, Set.of()),
			exercise("cable_fly", "Cable fly", false, Set.of("cables"))));

		assertThat(catalogService.listExercisesForUser(userId, null, false))
			.extracting(ExerciseResponse::id)
			.containsExactly("push_up");
	}

	@Test
	void mineCombinesWithTheSearchAndTheRetiredRule() {
		UUID userId = givenUserWithEquipment("cables");
		when(exerciseRepository.findAllWithLinks()).thenReturn(List.of(
			exercise("cable_fly", "Cable fly", false, Set.of("cables")),
			exercise("old_cable_fly", "Old cable fly", true, Set.of("cables")),
			exercise("cable_row", "Cable row", false, Set.of("cables")),
			exercise("barbell_fly", "Barbell fly", false, Set.of("barbell"))));

		assertThat(catalogService.listExercisesForUser(userId, "fly", false))
			.extracting(ExerciseResponse::id)
			.containsExactly("cable_fly");
		assertThat(catalogService.listExercisesForUser(userId, "fly", true))
			.extracting(ExerciseResponse::id)
			.containsExactly("cable_fly", "old_cable_fly");
	}

	@Test
	void exerciseRegionsAreSplitIntoPrimaryAndSecondaryInPositionOrder() {
		Exercise row = new Exercise("row", "Row", "horizontal_pull", 10, 12, "Cue.", "Note.", false,
			Set.of("cables"),
			Set.of(new ExerciseRegion("lats_lower", RegionRole.PRIMARY, 0),
				new ExerciseRegion("traps_mid", RegionRole.SECONDARY, 0),
				new ExerciseRegion("biceps_long", RegionRole.SECONDARY, 1)));
		when(exerciseRepository.findById("row")).thenReturn(Optional.of(row));

		ExerciseDetailResponse detail = catalogService.getExercise("row", null);

		assertThat(detail.primary()).containsExactly("lats_lower");
		assertThat(detail.secondary()).containsExactly("traps_mid", "biceps_long"); // by position, not alphabetical
		assertThat(detail.equipment()).containsExactly("cables");
		assertThat(detail.repRange()).containsExactly(10, 12);
		assertThat(detail.note()).isEqualTo("Note.");
		assertThat(detail.best()).isNull();
		assertThat(detail.last()).isNull();
	}

	@Test
	void unknownExerciseThrowsNotFound() {
		when(exerciseRepository.findById("nope")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> catalogService.getExercise("nope", null))
			.isInstanceOf(NotFoundException.class)
			.hasMessage("Exercise not found: nope");
	}

	@Test
	void regionsAreMappedToTheContractFields() {
		when(regionRepository.findAllByOrderBySortOrderAsc())
			.thenReturn(List.of(new Region("lats_upper", "Upper lats", "back", "back", 90)));

		assertThat(catalogService.listRegions())
			.containsExactly(new RegionResponse("lats_upper", "Upper lats", "back", "back", 90));
	}

	private static Exercise exercise(String id, String name, boolean retired) {
		return exercise(id, name, retired, Set.of());
	}

	private static Exercise exercise(String id, String name, boolean retired, Set<String> equipment) {
		return new Exercise(id, name, "squat", 8, 12, "Cue.", null, retired, equipment,
			Set.of(new ExerciseRegion("quads_vasti", RegionRole.PRIMARY, 0)));
	}

	private UUID givenUserWithEquipment(String... equipment) {
		UUID userId = UUID.randomUUID();
		UserSettings settings = new UserSettings(userId, "UTC");
		settings.getEquipmentIds().addAll(List.of(equipment));
		when(settingsRepository.findById(userId)).thenReturn(Optional.of(settings));
		return userId;
	}

}
