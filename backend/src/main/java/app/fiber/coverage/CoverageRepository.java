package app.fiber.coverage;

import app.fiber.workout.WorkoutSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Read-only queries for coverage and recovery. Each one loads everything it needs in a single query.
 *
 * "Counted" sets are completed sets that are not warm-ups, in FINISHED workouts of this user.
 * Workouts in progress never count.
 */
public interface CoverageRepository extends Repository<WorkoutSet, UUID> {

	/**
	 * Every counted set of workouts that started in [from, to), joined with the regions of its
	 * exercise: one row per (set, region).
	 */
	@Query("""
		select new app.fiber.coverage.CountedSetRegion(ws.id, ex.name, s.startedAt, r.regionId, r.role)
		from WorkoutSet ws
			join ws.sessionExercise e
			join e.session s
			join Exercise ex on ex.id = e.exerciseId
			join ex.regions r
		where s.userId = :userId
			and s.status = app.fiber.workout.SessionStatus.FINISHED
			and s.startedAt >= :from and s.startedAt < :to
			and ws.completed = true and ws.warmup = false""")
	List<CountedSetRegion> findCountedSetRegions(@Param("userId") UUID userId, @Param("from") Instant from,
		@Param("to") Instant to);

	/** Start times of the user's finished workouts that started in [from, to), including workouts without sets. */
	@Query("""
		select s.startedAt from WorkoutSession s
		where s.userId = :userId
			and s.status = app.fiber.workout.SessionStatus.FINISHED
			and s.startedAt >= :from and s.startedAt < :to""")
	List<Instant> findFinishedStartTimes(@Param("userId") UUID userId, @Param("from") Instant from,
		@Param("to") Instant to);

	/**
	 * For each workout that finished at or after {@code since}: how many counted sets it had per
	 * (region, role). The weighting (1 for primary, 0.5 for secondary) is applied in Java.
	 */
	@Query("""
		select new app.fiber.coverage.SessionRegionSets(s.id, s.finishedAt, r.regionId, r.role, count(ws.id))
		from WorkoutSet ws
			join ws.sessionExercise e
			join e.session s
			join Exercise ex on ex.id = e.exerciseId
			join ex.regions r
		where s.userId = :userId
			and s.status = app.fiber.workout.SessionStatus.FINISHED
			and s.finishedAt >= :since
			and ws.completed = true and ws.warmup = false
		group by s.id, s.finishedAt, r.regionId, r.role""")
	List<SessionRegionSets> findSessionRegionSetsFinishedSince(@Param("userId") UUID userId,
		@Param("since") Instant since);

}
