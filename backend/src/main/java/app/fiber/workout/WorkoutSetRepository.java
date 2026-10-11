package app.fiber.workout;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkoutSetRepository extends JpaRepository<WorkoutSet, UUID> {

	/** The id of the workout this set belongs to, but only if that workout belongs to this user. */
	@Query("""
		select ws.sessionExercise.session.id from WorkoutSet ws
		where ws.id = :setId and ws.sessionExercise.session.userId = :userId""")
	Optional<UUID> findSessionIdOwnedBy(@Param("setId") UUID setId, @Param("userId") UUID userId);

	/**
	 * Every completed set (warm-ups too) of these exercises in the user's FINISHED workouts.
	 * Newest workout first; within a workout in the order they were done (exercise position, set number).
	 */
	@Query("""
		select new app.fiber.workout.FinishedSetRow(e.exerciseId, s.id, s.startedAt, ws.weightKg, ws.reps, ws.warmup)
		from WorkoutSet ws
			join ws.sessionExercise e
			join e.session s
		where s.userId = :userId
			and s.status = app.fiber.workout.SessionStatus.FINISHED
			and e.exerciseId in :exerciseIds
			and ws.completed = true
		order by s.startedAt desc, s.id, e.position, ws.setNumber""")
	List<FinishedSetRow> findCompletedSetsInFinishedWorkouts(@Param("userId") UUID userId,
		@Param("exerciseIds") Collection<String> exerciseIds);

}
