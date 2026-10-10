package app.fiber.catalog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads the reference data (regions, equipment, exercises) from the seed folder at startup.
 *
 * The folder is the property {@code fiber.seed-dir} (env SEED_DIR, default ../data).
 * Rules:
 * - Upsert by id, all in one transaction: either everything is saved or nothing is.
 * - An exercise's equipment and region links are replaced by the ones in the file.
 * - Rows that are missing from the files are left alone, so old workouts stay valid.
 * - Broken references or mappings (unknown id, no primary region, a region listed twice)
 *   stop the app with a clear message.
 * - The order of each exercise's primary and secondary lists is kept (first primary = main region).
 * - The order of equipment.json is kept, so the equipment list shows it in the same order.
 * - If the folder doesn't exist, we log a warning and skip, so the app still starts without data.
 */
@Component
public class SeedLoader implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

	private final Path seedDir;
	private final JsonMapper jsonMapper;
	private final TransactionTemplate transactionTemplate;
	private final RegionRepository regionRepository;
	private final EquipmentRepository equipmentRepository;
	private final ExerciseRepository exerciseRepository;

	public SeedLoader(@Value("${fiber.seed-dir}") String seedDir, JsonMapper jsonMapper,
			TransactionTemplate transactionTemplate, RegionRepository regionRepository,
			EquipmentRepository equipmentRepository, ExerciseRepository exerciseRepository) {
		this.seedDir = Path.of(seedDir);
		this.jsonMapper = jsonMapper;
		this.transactionTemplate = transactionTemplate;
		this.regionRepository = regionRepository;
		this.equipmentRepository = equipmentRepository;
		this.exerciseRepository = exerciseRepository;
	}

	/** Called once by Spring Boot after startup. An exception here stops the app. */
	@Override
	public void run(ApplicationArguments args) {
		load(seedDir);
	}

	/** Reads the three seed files from {@code folder} and upserts them. Public so tests can load other folders. */
	public void load(Path folder) {
		Path dir = folder.toAbsolutePath().normalize();
		if (!Files.isDirectory(dir)) {
			// Deliberately not an error: the backend can run (e.g. for a quick API test) without the
			// data folder. The catalog then keeps whatever the database already has (empty on a new one).
			log.warn("Seed folder {} not found. Skipping seed loading; the exercise catalog may be empty. "
				+ "Set SEED_DIR to Fiber's data folder.", dir);
			return;
		}

		List<SeedRegion> regions = readFile(dir, "regions.json", SeedRegion[].class);
		List<SeedEquipment> equipment = readFile(dir, "equipment.json", SeedEquipment[].class);
		List<SeedExercise> exercises = readFile(dir, "exercises.json", SeedExercise[].class);
		// Check everything before writing anything.
		checkReferences(regions, equipment, exercises);

		transactionTemplate.executeWithoutResult(status -> save(regions, equipment, exercises));
		log.info("Seed data loaded from {}: {} regions, {} equipment, {} exercises.",
			dir, regions.size(), equipment.size(), exercises.size());
	}

	private <T> List<T> readFile(Path dir, String fileName, Class<T[]> type) {
		Path file = dir.resolve(fileName);
		if (!Files.isRegularFile(file)) {
			throw new IllegalStateException("Seed file missing: " + file);
		}
		try {
			return List.of(jsonMapper.readValue(file.toFile(), type));
		} catch (JacksonException e) {
			throw new IllegalStateException("Could not read seed file " + file + ": " + e.getOriginalMessage(), e);
		}
	}

	/**
	 * Checks each exercise before anything is written:
	 * - every region and equipment id it uses is defined in regions.json / equipment.json;
	 * - it has at least one primary region (coverage and the recommender need a main region);
	 * - no region appears twice (twice in one list, or as both primary and secondary);
	 * - repRange is exactly [low, high].
	 */
	private void checkReferences(List<SeedRegion> regions, List<SeedEquipment> equipment,
			List<SeedExercise> exercises) {
		Set<String> regionIds = regions.stream().map(SeedRegion::id).collect(Collectors.toSet());
		Set<String> equipmentIds = equipment.stream().map(SeedEquipment::id).collect(Collectors.toSet());

		for (SeedExercise exercise : exercises) {
			Set<String> usedRegions = new HashSet<>(exercise.primary());
			usedRegions.addAll(exercise.secondary());
			requireKnown(regionIds, usedRegions, "region", exercise.id(), "regions.json");
			requireKnown(equipmentIds, exercise.equipment(), "equipment", exercise.id(), "equipment.json");
			requireAtLeastOnePrimary(exercise);
			requireNoRepeats(exercise, exercise.primary(), "primary");
			requireNoRepeats(exercise, exercise.secondary(), "secondary");
			requireNotBothPrimaryAndSecondary(exercise);
			if (exercise.repRange().size() != 2) {
				throw new IllegalStateException("Seed data error: exercise '" + exercise.id()
					+ "' needs a repRange of exactly two numbers [low, high], got " + exercise.repRange());
			}
		}
	}

	private void requireKnown(Set<String> knownIds, Iterable<String> usedIds, String kind, String exerciseId,
			String fileName) {
		for (String id : usedIds) {
			if (!knownIds.contains(id)) {
				throw new IllegalStateException("Seed data error: exercise '" + exerciseId + "' uses unknown "
					+ kind + " id '" + id + "' (not in " + fileName + ")");
			}
		}
	}

	private void requireAtLeastOnePrimary(SeedExercise exercise) {
		if (exercise.primary().isEmpty()) {
			throw new IllegalStateException("Seed data error: exercise '" + exercise.id()
				+ "' has no primary region (it needs at least one)");
		}
	}

	private void requireNoRepeats(SeedExercise exercise, List<String> regionIds, String listName) {
		Set<String> seen = new HashSet<>();
		for (String regionId : regionIds) {
			if (!seen.add(regionId)) {
				throw new IllegalStateException("Seed data error: exercise '" + exercise.id() + "' lists region '"
					+ regionId + "' more than once in " + listName);
			}
		}
	}

	private void requireNotBothPrimaryAndSecondary(SeedExercise exercise) {
		for (String regionId : exercise.primary()) {
			if (exercise.secondary().contains(regionId)) {
				throw new IllegalStateException("Seed data error: exercise '" + exercise.id() + "' lists region '"
					+ regionId + "' as both primary and secondary");
			}
		}
	}

	private void save(List<SeedRegion> regions, List<SeedEquipment> equipment, List<SeedExercise> exercises) {
		// save() works as an upsert here: we set the ids ourselves, so Spring Data "merges" each entity.
		// It updates the row if the id exists and inserts it otherwise. For an exercise, merging also
		// replaces its equipment and region links with exactly the ones from the file.
		regionRepository.saveAll(regions.stream().map(SeedLoader::toRegion).toList());
		equipmentRepository.saveAll(toEquipment(equipment));
		exerciseRepository.saveAll(exercises.stream().map(SeedLoader::toExercise).toList());
	}

	private static Region toRegion(SeedRegion seed) {
		return new Region(seed.id(), seed.name(), seed.group(), seed.view(), seed.order());
	}

	/** Stores each item's index in the file as its position (0 = first), so the file order survives. */
	private static List<Equipment> toEquipment(List<SeedEquipment> seeds) {
		List<Equipment> equipment = new ArrayList<>();
		for (int position = 0; position < seeds.size(); position++) {
			SeedEquipment seed = seeds.get(position);
			equipment.add(new Equipment(seed.id(), seed.name(), position));
		}
		return equipment;
	}

	private static Exercise toExercise(SeedExercise seed) {
		Set<ExerciseRegion> links = new HashSet<>();
		addRegionLinks(links, seed.primary(), RegionRole.PRIMARY);
		addRegionLinks(links, seed.secondary(), RegionRole.SECONDARY);
		boolean retired = Boolean.TRUE.equals(seed.retired()); // missing in the file = not retired
		return new Exercise(seed.id(), seed.name(), seed.pattern(), seed.repRange().get(0), seed.repRange().get(1),
			seed.cue(), seed.note(), retired, new HashSet<>(seed.equipment()), links);
	}

	/** Stores each region's index within its list, so the file order survives (0 = first = main region). */
	private static void addRegionLinks(Set<ExerciseRegion> links, List<String> regionIds, RegionRole role) {
		for (int position = 0; position < regionIds.size(); position++) {
			links.add(new ExerciseRegion(regionIds.get(position), role, position));
		}
	}

	// ---- The JSON file formats (same as data/*.json). Extra fields in the files are ignored. ----

	record SeedRegion(String id, String name, String group, String view, int order) {
	}

	record SeedEquipment(String id, String name) {
	}

	record SeedExercise(String id, String name, List<String> equipment, String pattern, List<String> primary,
			List<String> secondary, List<Integer> repRange, String cue, String note, Boolean retired) {

		// A list left out of the file counts as empty, so the rest of the loader never sees null.
		SeedExercise {
			equipment = equipment == null ? List.of() : equipment;
			primary = primary == null ? List.of() : primary;
			secondary = secondary == null ? List.of() : secondary;
			repRange = repRange == null ? List.of() : repRange;
		}
	}

}
