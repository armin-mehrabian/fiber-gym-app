package app.fiber.catalog;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An exercise from the library (table exercise), with its equipment and region links.
 *
 * The links are plain ids, not entity references: an exercise "has" a set of equipment ids
 * (exercise_equipment) and a set of region roles (exercise_region). The database foreign keys
 * still make sure every id exists.
 */
@Entity
@Table(name = "exercise")
public class Exercise {

	@Id
	private String id;

	private String name;

	private String pattern;

	@Column(name = "rep_low")
	private int repLow;

	@Column(name = "rep_high")
	private int repHigh;

	// One short how-to sentence.
	private String cue;

	// Optional, e.g. a weak-evidence warning. Null when there is none.
	private String note;

	// Retired exercises stay in the table (old workouts point to them) but are hidden from lists.
	private boolean retired;

	// Empty set = bodyweight exercise.
	@ElementCollection
	@CollectionTable(name = "exercise_equipment", joinColumns = @JoinColumn(name = "exercise_id"))
	@Column(name = "equipment_id")
	private Set<String> equipmentIds = new HashSet<>();

	@ElementCollection
	@CollectionTable(name = "exercise_region", joinColumns = @JoinColumn(name = "exercise_id"))
	private Set<ExerciseRegion> regions = new HashSet<>();

	protected Exercise() {
		// for JPA
	}

	public Exercise(String id, String name, String pattern, int repLow, int repHigh, String cue, String note,
			boolean retired, Set<String> equipmentIds, Set<ExerciseRegion> regions) {
		this.id = id;
		this.name = name;
		this.pattern = pattern;
		this.repLow = repLow;
		this.repHigh = repHigh;
		this.cue = cue;
		this.note = note;
		this.retired = retired;
		this.equipmentIds = new HashSet<>(equipmentIds);
		this.regions = new HashSet<>(regions);
	}

	/**
	 * True if every piece of equipment this exercise needs is in {@code available}.
	 * Bodyweight exercises need nothing, so they are always possible.
	 */
	public boolean canBeDoneWith(Set<String> available) {
		return available.containsAll(equipmentIds);
	}

	/** Equipment ids, sorted alphabetically so API output is stable. */
	public List<String> sortedEquipmentIds() {
		return equipmentIds.stream().sorted().toList();
	}

	/** Region ids with the given role, in the order of the seed file (the first primary is the main region). */
	public List<String> regionIds(RegionRole role) {
		return regions.stream()
			.filter(link -> link.role() == role)
			.sorted(Comparator.comparingInt(ExerciseRegion::position))
			.map(ExerciseRegion::regionId)
			.toList();
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getPattern() {
		return pattern;
	}

	public int getRepLow() {
		return repLow;
	}

	public int getRepHigh() {
		return repHigh;
	}

	public String getCue() {
		return cue;
	}

	public String getNote() {
		return note;
	}

	public boolean isRetired() {
		return retired;
	}

}
