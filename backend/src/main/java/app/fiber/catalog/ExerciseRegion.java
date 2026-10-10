package app.fiber.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * One row of exercise_region: "this exercise emphasises this region, as PRIMARY or SECONDARY".
 * The exercise id column comes from the collection mapping in {@link Exercise}.
 *
 * position is the region's index within its list (primary or secondary) in exercises.json.
 * The primary with position 0 is the exercise's main region (used by the recommender).
 */
@Embeddable
public record ExerciseRegion(
	@Column(name = "region_id") String regionId,
	@Enumerated(EnumType.STRING) RegionRole role,
	@Column(name = "position") int position) {
}
