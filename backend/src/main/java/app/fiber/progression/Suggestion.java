package app.fiber.progression;

import java.math.BigDecimal;

/** What to aim for in the next workout. weightKg is null when there is no history to base it on. */
public record Suggestion(BigDecimal weightKg, int reps, SuggestionReason reason) {
}
