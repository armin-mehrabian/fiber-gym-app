package app.fiber.settings;

import java.util.List;

/** Body of GET/PUT /api/settings. splitType is null until the user has picked a split. */
public record SettingsResponse(
	SplitType splitType,
	Units units,
	String timezone,
	boolean onboarded,
	List<String> equipment,
	List<SplitDayResponse> splitDays) {
}
