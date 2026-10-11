package app.fiber.workout;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, UUID> {

	/** Only finds the session if it belongs to this user, so nobody can open someone else's workout. */
	Optional<WorkoutSession> findByIdAndUserId(UUID id, UUID userId);

	/**
	 * Like findByIdAndUserId, but also locks the workout's row (SELECT ... FOR UPDATE) until the
	 * transaction ends. Used before every change, so two requests for the same workout (e.g. a
	 * double tap on "+ SET") run one after the other and never pick the same set number or position.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from WorkoutSession s where s.id = :id and s.userId = :userId")
	Optional<WorkoutSession> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

	Optional<WorkoutSession> findByUserIdAndStatus(UUID userId, SessionStatus status);

	boolean existsByUserIdAndStatus(UUID userId, SessionStatus status);

	/** The user's most recently finished workout (pass FINISHED), e.g. to find the next split day. */
	Optional<WorkoutSession> findFirstByUserIdAndStatusOrderByFinishedAtDesc(UUID userId, SessionStatus status);

	/**
	 * The user's finished workouts started at or after {@code from}, newest first, with two counts each:
	 * how many exercises, and how many working sets (completed and not a warm-up).
	 * Left joins, so a workout without exercises or sets still shows up with 0.
	 */
	@Query("""
		select new app.fiber.workout.HistorySession(
			s.id, s.name, s.startedAt, s.finishedAt,
			count(distinct e.id),
			coalesce(sum(case when ws.completed = true and ws.warmup = false then 1 else 0 end), 0))
		from WorkoutSession s
			left join s.exercises e
			left join e.sets ws
		where s.userId = :userId and s.status = app.fiber.workout.SessionStatus.FINISHED and s.startedAt >= :from
		group by s.id, s.name, s.startedAt, s.finishedAt
		order by s.startedAt desc""")
	List<HistorySession> findFinishedSince(@Param("userId") UUID userId, @Param("from") Instant from);

}
