package app.fiber.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

/** Primary key of region_target: one row per user and region. */
@Embeddable
public record RegionTargetId(
	@Column(name = "user_id") UUID userId,
	@Column(name = "region_id") String regionId) implements Serializable {
}
