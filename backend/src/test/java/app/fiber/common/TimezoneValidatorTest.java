package app.fiber.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TimezoneValidatorTest {

	private final TimezoneValidator validator = new TimezoneValidator();

	@ParameterizedTest
	@ValueSource(strings = { "America/Vancouver", "Europe/Berlin", "Asia/Tehran", "UTC" })
	void ianaZonesAreValid(String zone) {
		assertThat(validator.isValid(zone, null)).isTrue();
	}

	// Fixed offsets ignore daylight saving time, so they'd put the week boundary in the wrong place half the year.
	@ParameterizedTest
	@ValueSource(strings = { "Mars/Olympus", "+02:00", "GMT+1", "europe/berlin", "" })
	void unknownZonesAndFixedOffsetsAreInvalid(String zone) {
		assertThat(validator.isValid(zone, null)).isFalse();
	}

	// "Required" is checked separately (@NotBlank), so null alone is not an error here.
	@ParameterizedTest
	@NullSource
	void nullIsLeftToNotBlank(String zone) {
		assertThat(validator.isValid(zone, null)).isTrue();
	}

}
