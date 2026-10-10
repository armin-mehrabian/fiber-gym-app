package app.fiber.common;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.ZoneId;
import java.util.Set;

/**
 * Checks {@link ValidTimezone}. Only named zones from the IANA database are accepted
 * (e.g. "Europe/Berlin", "UTC"); fixed offsets like "+02:00" are not, because they
 * ignore daylight saving time and would shift the user's week boundary.
 */
public class TimezoneValidator implements ConstraintValidator<ValidTimezone, String> {

	private static final Set<String> IANA_ZONES = ZoneId.getAvailableZoneIds();

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || IANA_ZONES.contains(value);
	}

}
