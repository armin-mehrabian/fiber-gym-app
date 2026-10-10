package app.fiber.settings;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * How many effective sets per week a user wants for one region (table region_target).
 * One row per region is created at signup (see DefaultTargets). 0 means "ignore this region".
 */
@Entity
@Table(name = "region_target")
public class RegionTarget {

	@EmbeddedId
	private RegionTargetId id;

	@Column(name = "weekly_sets")
	private int weeklySets;

	protected RegionTarget() {
		// for JPA
	}

	public RegionTarget(UUID userId, String regionId, int weeklySets) {
		this.id = new RegionTargetId(userId, regionId);
		this.weeklySets = weeklySets;
	}

	public String getRegionId() {
		return id.regionId();
	}

	public int getWeeklySets() {
		return weeklySets;
	}

	public void setWeeklySets(int weeklySets) {
		this.weeklySets = weeklySets;
	}

}
