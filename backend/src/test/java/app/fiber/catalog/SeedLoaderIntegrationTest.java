package app.fiber.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.fiber.TestcontainersConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The seed loader against a real PostgreSQL (Testcontainers, needs Docker).
 * The app loads the fixture set (src/test/resources/seed) at startup. Some tests load a changed
 * copy from a temp folder; after each test the fixture is loaded again, so the next test starts clean.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SeedLoaderIntegrationTest {

	@Autowired
	private SeedLoader seedLoader;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Value("${fiber.seed-dir}")
	private String fixtureDir;

	@TempDir
	private Path tempDir;

	@AfterEach
	void reloadFixture() {
		seedLoader.load(Path.of(fixtureDir));
	}

	@Test
	void startupLoadsTheFixture() {
		assertFixtureRowCounts();

		Map<String, Object> region = jdbcTemplate.queryForMap(
			"select name, muscle_group, view, sort_order from region where id = 'lats_upper'");
		assertThat(region).containsEntry("name", "Upper lats").containsEntry("muscle_group", "back")
			.containsEntry("view", "back").containsEntry("sort_order", 32);

		Map<String, Object> row = jdbcTemplate.queryForMap(
			"select name, pattern, rep_low, rep_high, cue, note, retired from exercise where id = 'single_arm_cable_row'");
		assertThat(row).containsEntry("name", "Single-arm cable row").containsEntry("pattern", "horizontal_pull")
			.containsEntry("rep_low", 10).containsEntry("rep_high", 12)
			.containsEntry("note", "Upper vs lower lat emphasis is weak evidence.")
			.containsEntry("retired", false);
		// role:position, where position is the index in the file's primary or secondary list
		assertThat(regionLinks("single_arm_cable_row"))
			.containsExactlyInAnyOrder("lats_lower:PRIMARY:0", "lats_upper:SECONDARY:0", "biceps_long:SECONDARY:1");
		assertThat(regionLinks("incline_barbell_press"))
			.containsExactlyInAnyOrder("chest_upper:PRIMARY:0", "chest_mid:PRIMARY:1", "delt_front:SECONDARY:0");
		assertThat(equipmentLinks("incline_barbell_press")).containsExactlyInAnyOrder("barbell", "bench");
	}

	@Test
	void bodyweightExerciseHasNoEquipmentAndRetiredFlagIsSaved() {
		assertThat(equipmentLinks("push_up")).isEmpty();
		assertThat(isRetired("barbell_floor_press")).isTrue();
		assertThat(isRetired("push_up")).isFalse(); // "retired" left out of the file = not retired
	}

	@Test
	void loadingTwiceDoesNotDuplicateRows() {
		seedLoader.load(Path.of(fixtureDir));

		assertFixtureRowCounts();
	}

	@Test
	void reloadingReplacesTheLinksOfAnExercise() throws IOException {
		// Equipment changed, roles swapped, biceps removed, note removed.
		Path changed = fixtureCopyWithExercises("""
			[{ "id": "single_arm_cable_row", "name": "Single-arm cable row", "equipment": ["barbell"],
			   "pattern": "horizontal_pull", "primary": ["lats_upper"], "secondary": ["lats_lower"],
			   "repRange": [8, 12], "cue": "New cue." }]""");

		seedLoader.load(changed);

		assertThat(regionLinks("single_arm_cable_row"))
			.containsExactlyInAnyOrder("lats_upper:PRIMARY:0", "lats_lower:SECONDARY:0");
		assertThat(equipmentLinks("single_arm_cable_row")).containsExactly("barbell");
		Map<String, Object> row = jdbcTemplate.queryForMap(
			"select rep_low, cue, note from exercise where id = 'single_arm_cable_row'");
		assertThat(row).containsEntry("rep_low", 8).containsEntry("cue", "New cue.").containsEntry("note", null);
	}

	@Test
	void exercisesMissingFromTheFileAreLeftAlone() throws IOException {
		Path changed = fixtureCopyWithExercises("""
			[{ "id": "push_up", "name": "Push-up", "equipment": [], "pattern": "horizontal_push",
			   "primary": ["chest_mid"], "secondary": ["delt_front"], "repRange": [8, 20], "cue": "Straight line." }]""");

		seedLoader.load(changed);

		assertThat(count("exercise")).isEqualTo(5);
		assertThat(regionLinks("cable_fly")).containsExactly("chest_mid:PRIMARY:0");
	}

	@Test
	void retiringAnExerciseInTheFileSetsTheFlag() throws IOException {
		Path changed = fixtureCopyWithExercises("""
			[{ "id": "push_up", "name": "Push-up", "equipment": [], "pattern": "horizontal_push",
			   "primary": ["chest_mid"], "secondary": [], "repRange": [8, 20], "cue": "Straight line.",
			   "retired": true }]""");

		seedLoader.load(changed);

		assertThat(isRetired("push_up")).isTrue();
	}

	@Test
	void unknownRegionIdFailsWithAClearMessageAndWritesNothing() throws IOException {
		Path broken = fixtureCopyWithExercises("""
			[{ "id": "single_arm_cable_row", "name": "Single-arm cable row", "equipment": ["cables"],
			   "pattern": "horizontal_pull", "primary": ["lats_middle"], "secondary": [],
			   "repRange": [10, 12], "cue": "Row." }]""");

		assertThatThrownBy(() -> seedLoader.load(broken))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("exercise 'single_arm_cable_row'")
			.hasMessageContaining("unknown region id 'lats_middle'");
		assertThat(regionLinks("single_arm_cable_row"))
			.containsExactlyInAnyOrder("lats_lower:PRIMARY:0", "lats_upper:SECONDARY:0", "biceps_long:SECONDARY:1");
	}

	@Test
	void reloadingWithAReorderedPrimaryListUpdatesTheOrder() throws IOException {
		// The main region changes from chest_upper to chest_mid.
		Path reordered = fixtureCopyWithExercises("""
			[{ "id": "incline_barbell_press", "name": "Incline barbell press", "equipment": ["barbell", "bench"],
			   "pattern": "horizontal_push", "primary": ["chest_mid", "chest_upper"], "secondary": ["delt_front"],
			   "repRange": [6, 10], "cue": "Lower the bar to your upper chest." }]""");

		seedLoader.load(reordered);

		assertThat(regionLinks("incline_barbell_press"))
			.containsExactlyInAnyOrder("chest_mid:PRIMARY:0", "chest_upper:PRIMARY:1", "delt_front:SECONDARY:0");
	}

	@Test
	void regionThatIsBothPrimaryAndSecondaryFailsWithAClearMessage() throws IOException {
		Path broken = fixtureCopyWithExercises("""
			[{ "id": "cable_fly", "name": "Cable fly", "equipment": ["cables"], "pattern": "fly",
			   "primary": ["chest_mid"], "secondary": ["chest_mid"], "repRange": [10, 15], "cue": "Hug." }]""");

		assertThatThrownBy(() -> seedLoader.load(broken))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("exercise 'cable_fly'")
			.hasMessageContaining("region 'chest_mid' as both primary and secondary");
	}

	@Test
	void exerciseWithoutPrimaryRegionFailsWithAClearMessage() throws IOException {
		Path broken = fixtureCopyWithExercises("""
			[{ "id": "cable_fly", "name": "Cable fly", "equipment": ["cables"], "pattern": "fly",
			   "primary": [], "secondary": ["chest_mid"], "repRange": [10, 15], "cue": "Hug." }]""");

		assertThatThrownBy(() -> seedLoader.load(broken))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("exercise 'cable_fly' has no primary region");
	}

	@Test
	void regionRepeatedInOneListFailsWithAClearMessage() throws IOException {
		Path broken = fixtureCopyWithExercises("""
			[{ "id": "single_arm_cable_row", "name": "Single-arm cable row", "equipment": ["cables"],
			   "pattern": "horizontal_pull", "primary": ["lats_lower"], "secondary": ["biceps_long", "biceps_long"],
			   "repRange": [10, 12], "cue": "Row." }]""");

		assertThatThrownBy(() -> seedLoader.load(broken))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("exercise 'single_arm_cable_row'")
			.hasMessageContaining("region 'biceps_long' more than once in secondary");
	}

	@Test
	void unknownEquipmentIdFailsWithAClearMessage() throws IOException {
		Path broken = fixtureCopyWithExercises("""
			[{ "id": "cable_fly", "name": "Cable fly", "equipment": ["kettlebells"], "pattern": "fly",
			   "primary": ["chest_mid"], "secondary": [], "repRange": [10, 15], "cue": "Hug." }]""");

		assertThatThrownBy(() -> seedLoader.load(broken))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("exercise 'cable_fly'")
			.hasMessageContaining("unknown equipment id 'kettlebells'");
	}

	@Test
	void missingSeedFolderIsSkipped() {
		assertThatCode(() -> seedLoader.load(tempDir.resolve("does-not-exist"))).doesNotThrowAnyException();

		assertFixtureRowCounts();
	}

	@Test
	void missingFileInAnExistingFolderFails() throws IOException {
		Files.copy(Path.of(fixtureDir, "regions.json"), tempDir.resolve("regions.json"));

		assertThatThrownBy(() -> seedLoader.load(tempDir))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("equipment.json");
	}

	// ---- helpers ----

	/** Fixture counts: 26 regions, 3 equipment, 5 exercises, 5 equipment links, 11 region links. */
	private void assertFixtureRowCounts() {
		assertThat(count("region")).isEqualTo(26);
		assertThat(count("equipment")).isEqualTo(3);
		assertThat(count("exercise")).isEqualTo(5);
		assertThat(count("exercise_equipment")).isEqualTo(5);
		assertThat(count("exercise_region")).isEqualTo(11);
	}

	/** A temp seed folder with the fixture's regions and equipment, and the given exercises.json. */
	private Path fixtureCopyWithExercises(String exercisesJson) throws IOException {
		Files.copy(Path.of(fixtureDir, "regions.json"), tempDir.resolve("regions.json"));
		Files.copy(Path.of(fixtureDir, "equipment.json"), tempDir.resolve("equipment.json"));
		Files.writeString(tempDir.resolve("exercises.json"), exercisesJson);
		return tempDir;
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
	}

	/** An exercise's region links as "region:ROLE:position", e.g. "lats_lower:PRIMARY:0". */
	private List<String> regionLinks(String exerciseId) {
		return jdbcTemplate.queryForList(
			"select region_id || ':' || role || ':' || position from exercise_region where exercise_id = ?",
			String.class, exerciseId);
	}

	private List<String> equipmentLinks(String exerciseId) {
		return jdbcTemplate.queryForList(
			"select equipment_id from exercise_equipment where exercise_id = ?", String.class, exerciseId);
	}

	private boolean isRetired(String exerciseId) {
		return jdbcTemplate.queryForObject("select retired from exercise where id = ?", Boolean.class, exerciseId);
	}

}
