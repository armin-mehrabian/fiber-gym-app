package app.fiber.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExerciseRepository extends JpaRepository<Exercise, String> {

	/**
	 * All exercises with their equipment and region links loaded in one query
	 * (instead of two extra queries per exercise).
	 */
	@EntityGraph(attributePaths = {"equipmentIds", "regions"})
	@Query("select e from Exercise e")
	List<Exercise> findAllWithLinks();

}
