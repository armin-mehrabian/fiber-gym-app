package app.fiber.settings;

import app.fiber.common.ValidTimezone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * Body of PUT /api/settings. splitDays is only read for a CUSTOM split; for the presets
 * (PPL, UL, FB) the server builds the days itself and ignores it.
 * The CUSTOM days are checked by the service, since they only matter for that split type.
 */
public record SettingsRequest(
	@NotNull(message = "is required")
	@Pattern(regexp = "PPL|UL|FB|CUSTOM", message = "must be PPL, UL, FB or CUSTOM")
	String splitType,

	@NotNull(message = "is required")
	@Pattern(regexp = "KG|LB", message = "must be KG or LB")
	String units,

	@NotBlank(message = "is required")
	@ValidTimezone
	String timezone,

	@NotNull(message = "is required")
	List<String> equipment,

	List<SplitDayRequest> splitDays) {
}
