package app.fiber.workout;

import java.time.LocalDate;
import java.util.List;

/** One Monday-Sunday week of GET /api/history. weekStart is the Monday, e.g. "2026-10-05". */
public record HistoryWeek(LocalDate weekStart, List<HistorySession> sessions) {
}
