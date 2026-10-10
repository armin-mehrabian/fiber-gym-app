package app.fiber.workout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * One logged set (table workout_set). Weight is always in kg; bodyweight exercises use 0.
 * Only completed, non-warm-up sets count as working sets.
 */
@Entity
@Table(name = "workout_set")
public class WorkoutSet {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "session_exercise_id")
	private SessionExercise sessionExercise;

	// 1, 2, 3 ... within its exercise, with no gaps.
	@Column(name = "set_number")
	private int setNumber;

	@Column(name = "weight_kg")
	private BigDecimal weightKg;

	private int reps;

	@Column(name = "is_warmup")
	private boolean warmup;

	private boolean completed;

	@Column(name = "logged_at")
	private Instant loggedAt;

	protected WorkoutSet() {
		// for JPA
	}

	WorkoutSet(SessionExercise sessionExercise, int setNumber, BigDecimal weightKg, int reps, boolean warmup,
			Instant now) {
		this.sessionExercise = sessionExercise;
		this.setNumber = setNumber;
		setWeightKg(weightKg);
		this.reps = reps;
		this.warmup = warmup;
		this.completed = false;
		this.loggedAt = now;
	}

	/** Always kept with 2 decimals, like the database column, so the JSON looks the same before and after saving. */
	public void setWeightKg(BigDecimal weightKg) {
		// Input is already checked to have at most 2 decimals, so this never actually rounds.
		this.weightKg = weightKg.setScale(2, RoundingMode.HALF_UP);
	}

	public void setReps(int reps) {
		this.reps = reps;
	}

	public void setWarmup(boolean warmup) {
		this.warmup = warmup;
	}

	public void setCompleted(boolean completed) {
		this.completed = completed;
	}

	void setSetNumber(int setNumber) {
		this.setNumber = setNumber;
	}

	public UUID getId() {
		return id;
	}

	public SessionExercise getSessionExercise() {
		return sessionExercise;
	}

	public int getSetNumber() {
		return setNumber;
	}

	public BigDecimal getWeightKg() {
		return weightKg;
	}

	public int getReps() {
		return reps;
	}

	public boolean isWarmup() {
		return warmup;
	}

	public boolean isCompleted() {
		return completed;
	}

	public Instant getLoggedAt() {
		return loggedAt;
	}

}
