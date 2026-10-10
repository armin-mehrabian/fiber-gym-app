package app.fiber.common;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value must be an IANA timezone name, e.g. "America/Vancouver" or "Europe/Berlin".
 * null counts as valid here; combine with @NotBlank when the value is required.
 */
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TimezoneValidator.class)
public @interface ValidTimezone {

	String message() default "must be a valid timezone, e.g. America/Vancouver";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
