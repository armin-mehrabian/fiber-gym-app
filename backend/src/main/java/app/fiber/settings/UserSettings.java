package app.fiber.settings;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A user's settings (table user_settings, one row per user), plus the equipment they have
 * (table user_equipment). Created at signup; filled in during onboarding.
 */
@Entity
@Table(name = "user_settings")
public class UserSettings {

	// Same id as the user's account.
	@Id
	@Column(name = "user_id")
	private UUID userId;

	// null until the user picks a split in onboarding.
	@Enumerated(EnumType.STRING)
	@Column(name = "split_type")
	private SplitType splitType;

	@Enumerated(EnumType.STRING)
	@Column(name = "units")
	private Units units;

	// IANA name, e.g. America/Vancouver. Decides where the user's Monday-Sunday week starts.
	@Column(name = "timezone")
	private String timezone;

	@Column(name = "onboarded")
	private boolean onboarded;

	@ElementCollection
	@CollectionTable(name = "user_equipment", joinColumns = @JoinColumn(name = "user_id"))
	@Column(name = "equipment_id")
	private Set<String> equipmentIds = new HashSet<>();

	protected UserSettings() {
		// for JPA
	}

	/** The settings of a brand-new user: kg, no split yet, not onboarded. */
	public UserSettings(UUID userId, String timezone) {
		this.userId = userId;
		this.units = Units.KG;
		this.timezone = timezone;
		this.onboarded = false;
	}

	/** Saves the onboarding / settings screen. Saving it once means the user is onboarded. */
	public void update(SplitType splitType, Units units, String timezone, Set<String> equipmentIds) {
		this.splitType = splitType;
		this.units = units;
		this.timezone = timezone;
		this.equipmentIds.clear();
		this.equipmentIds.addAll(equipmentIds);
		this.onboarded = true;
	}

	public UUID getUserId() {
		return userId;
	}

	public SplitType getSplitType() {
		return splitType;
	}

	public Units getUnits() {
		return units;
	}

	public String getTimezone() {
		return timezone;
	}

	public boolean isOnboarded() {
		return onboarded;
	}

	public Set<String> getEquipmentIds() {
		return equipmentIds;
	}

}
