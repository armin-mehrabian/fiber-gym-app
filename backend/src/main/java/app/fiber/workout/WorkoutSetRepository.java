package app.fiber.workout;

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

}
