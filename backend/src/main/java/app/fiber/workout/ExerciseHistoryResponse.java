package app.fiber.workout;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** One finished workout in GET /api/exercises/{id}/history: its day and the exercise's completed sets. */
public record ExerciseHistoryResponse(UUID sessionId, LocalDate date, List<HistorySetResponse> sets) {
}
