package app.fiber.workout;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

/**
 * One exercise inside a workout (table session_exercise): which exercise, its place in the
 * workout (position, from 1), the plan for it, and the sets logged so far (by set number).
 *
 * The rep range is copied from the exercise when it is added, so an old workout keeps the
 * range it was done with even if the library changes later.
 */
@Entity
@Table(name = "session_exercise")
public class SessionExercise {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "session_id")
	private WorkoutSession session;

	private int position;

	@Column(name = "exercise_id")
	private String exerciseId;

	@Column(name = "target_sets")
	private int targetSets;

	@Column(name = "rep_low")
	private int repLow;

	@Column(name = "rep_high")
	private int repHigh;

	// Filled in by the weight suggestions later; null for now.
	@Column(name = "suggested_weight_kg")
	private BigDecimal suggestedWeightKg;

	// Load the sets of up to 50 exercises in one query, instead of one query per exercise.
	@BatchSize(size = 50)
	@OneToMany(mappedBy = "sessionExercise", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("setNumber")
	private List<WorkoutSet> sets = new ArrayList<>();

	protected SessionExercise() {
		// for JPA
	}

	SessionExercise(WorkoutSession session, int position, String exerciseId, int targetSets, int repLow,
			int repHigh) {
		this.session = session;
		this.position = position;
		this.exerciseId = exerciseId;
		this.targetSets = targetSets;
		this.repLow = repLow;
		this.repHigh = repHigh;
	}

	/** Appends a new, not yet completed set with the next set number. */
	public WorkoutSet addSet(BigDecimal weightKg, int reps, boolean warmup, Instant now) {
		WorkoutSet set = new WorkoutSet(this, sets.size() + 1, weightKg, reps, warmup, now);
		sets.add(set);
		return set;
	}

	/** Removes the set and numbers the remaining ones 1..n again, keeping their order. */
	public void removeSet(WorkoutSet set) {
		sets.remove(set);
		for (int i = 0; i < sets.size(); i++) {
			sets.get(i).setSetNumber(i + 1);
		}
	}

	void setPosition(int position) {
		this.position = position;
	}

	public UUID getId() {
		return id;
	}

	public WorkoutSession getSession() {
		return session;
	}

	public int getPosition() {
		return position;
	}

	public String getExerciseId() {
		return exerciseId;
	}

	public int getTargetSets() {
		return targetSets;
	}

	public int getRepLow() {
		return repLow;
	}

	public int getRepHigh() {
		return repHigh;
	}

	public BigDecimal getSuggestedWeightKg() {
		return suggestedWeightKg;
	}

	public List<WorkoutSet> getSets() {
		return sets;
	}

}
