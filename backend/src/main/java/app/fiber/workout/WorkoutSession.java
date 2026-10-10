package app.fiber.workout;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One workout of one user (table workout_session), with its exercises in order.
 * Deleting a session also deletes its exercises and their sets.
 */
@Entity
@Table(name = "workout_session")
public class WorkoutSession {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id")
	private UUID userId;

	// The split day this workout was started for, or null (no day picked, or the day was deleted later).
	@Column(name = "split_day_id")
	private UUID splitDayId;

	private String name;

	@Enumerated(EnumType.STRING)
	private SessionStatus status;

	@Column(name = "started_at")
	private Instant startedAt;

	// null while the workout is ACTIVE.
	@Column(name = "finished_at")
	private Instant finishedAt;

	@OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position")
	private List<SessionExercise> exercises = new ArrayList<>();

	protected WorkoutSession() {
		// for JPA
	}

	/** A new, empty, ACTIVE workout. */
	public WorkoutSession(UUID userId, UUID splitDayId, String name, Instant startedAt) {
		this.userId = userId;
		this.splitDayId = splitDayId;
		this.name = name;
		this.status = SessionStatus.ACTIVE;
		this.startedAt = startedAt;
	}

	public boolean isFinished() {
		return status == SessionStatus.FINISHED;
	}

	public void finish(Instant now) {
		this.status = SessionStatus.FINISHED;
		this.finishedAt = now;
	}

	/** Appends an exercise at the end; positions start at 1. */
	public SessionExercise addExercise(String exerciseId, int targetSets, int repLow, int repHigh) {
		SessionExercise exercise = new SessionExercise(this, exercises.size() + 1, exerciseId, targetSets, repLow,
			repHigh);
		exercises.add(exercise);
		return exercise;
	}

	public Optional<SessionExercise> exerciseAt(int position) {
		return exercises.stream().filter(exercise -> exercise.getPosition() == position).findFirst();
	}

	/** Takes the exercise out of the list; its row and sets are deleted on the next flush. */
	public void removeExercise(SessionExercise exercise) {
		exercises.remove(exercise);
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getSplitDayId() {
		return splitDayId;
	}

	public String getName() {
		return name;
	}

	public SessionStatus getStatus() {
		return status;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public List<SessionExercise> getExercises() {
		return exercises;
	}

}
