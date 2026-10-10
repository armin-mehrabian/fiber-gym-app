package app.fiber.settings;

/**
 * How the user splits training over days. PPL = push/pull/legs, UL = upper/lower,
 * FB = full body, CUSTOM = days the user defines. The first three are built by {@link SplitPresets}.
 */
public enum SplitType {
	PPL, UL, FB, CUSTOM
}
