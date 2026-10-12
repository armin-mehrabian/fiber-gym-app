package app.fiber.coverage;

/**
 * How far a region is this week, in words instead of numbers:
 * - NOT_YET: no effective sets at all
 * - STARTED: some sets, but fewer than the weekly target
 * - DONE: the weekly target is reached (a target of 0 also counts as done: nothing is asked for)
 */
public enum RegionState {
	NOT_YET,
	STARTED,
	DONE
}
