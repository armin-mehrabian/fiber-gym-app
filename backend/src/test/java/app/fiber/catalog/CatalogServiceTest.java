package app.fiber.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.fiber.common.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Plain unit test with mocked repositories: filtering, sorting and the entity-to-JSON mapping.
 */
class CatalogServiceTest {

	private final RegionRepository regionRepository = mock(RegionRepository.class);
	private final EquipmentRepository equipmentRepository = mock(EquipmentRepository.class);
	private final ExerciseRepository exerciseRepository = mock(ExerciseRepository.class);
	private final CatalogService catalogService =
		new CatalogService(regionRepository, equipmentRepository, exerciseRepository);

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

	@Test
	void exerciseRegionsAreSplitIntoPrimaryAndSecondaryInPositionOrder() {
		Exercise row = new Exercise("row", "Row", "horizontal_pull", 10, 12, "Cue.", "Note.", false,
			Set.of("cables"),
			Set.of(new ExerciseRegion("lats_lower", RegionRole.PRIMARY, 0),
				new ExerciseRegion("traps_mid", RegionRole.SECONDARY, 0),
				new ExerciseRegion("biceps_long", RegionRole.SECONDARY, 1)));
		when(exerciseRepository.findById("row")).thenReturn(Optional.of(row));

		ExerciseDetailResponse detail = catalogService.getExercise("row");

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

		assertThatThrownBy(() -> catalogService.getExercise("nope"))
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
		return new Exercise(id, name, "squat", 8, 12, "Cue.", null, retired, Set.of(),
			Set.of(new ExerciseRegion("quads_vasti", RegionRole.PRIMARY, 0)));
	}

}
