package app.fiber.progression;

/**
 * Why a weight and rep target was suggested.
 * NO_HISTORY = never done before: no weight, aim for the top of the rep range.
 * INCREASE   = every set reached the top of the range last time: more weight, back to the bottom of the range.
 * REPEAT     = not every set reached the top: same weight, one more rep.
 * MORE_REPS  = a bodyweight exercise: one more rep than the best set last time.
 */
public enum SuggestionReason {
	NO_HISTORY,
	INCREASE,
	REPEAT,
	MORE_REPS
}
