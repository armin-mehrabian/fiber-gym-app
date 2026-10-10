package app.fiber.settings;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One day of a user's split, e.g. "Push" (table split_day), with the regions it trains
 * (table split_day_region). position = the day's place in the rotation, starting at 1.
 */
@Entity
@Table(name = "split_day")
public class SplitDay {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "position")
	private int position;

	@Column(name = "name")
	private String name;

	@ElementCollection
	@CollectionTable(name = "split_day_region", joinColumns = @JoinColumn(name = "split_day_id"))
	@Column(name = "region_id")
	private Set<String> regionIds = new HashSet<>();

	protected SplitDay() {
		// for JPA
	}

	public SplitDay(UUID userId, int position, String name, List<String> regionIds) {
		this.userId = userId;
		this.position = position;
		this.name = name;
		this.regionIds.addAll(regionIds);
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public int getPosition() {
		return position;
	}

	public String getName() {
		return name;
	}

	public Set<String> getRegionIds() {
		return regionIds;
	}

}
