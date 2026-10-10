package app.fiber.workout;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Date math for Fiber's training week: Monday 00:00 to Sunday 23:59 in the user's own timezone.
 *
 * Timestamps are stored in UTC, so the same moment can fall into different weeks for different
 * users: Monday 00:30 in Auckland is still Sunday in Vancouver. Pure Java, no Spring, no database.
 */
public final class WeekMath {

	private WeekMath() {
	}

	/** The Monday of the week that contains this date. */
	public static LocalDate weekStart(LocalDate date) {
		return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	/** The Monday (in the user's timezone) of the week this moment falls into for that user. */
	public static LocalDate weekStart(Instant moment, ZoneId zone) {
		return weekStart(moment.atZone(zone).toLocalDate());
	}

	/**
	 * The exact moment a week begins: Monday 00:00 in the user's timezone.
	 * If a clock change skips midnight in that zone, it is the first moment that does exist that day.
	 */
	public static Instant startOf(LocalDate monday, ZoneId zone) {
		return monday.atStartOfDay(zone).toInstant();
	}

	/**
	 * Where a history of {@code weeks} weeks begins: the current week counts as week 1,
	 * so weeks = 1 means "this week only" and weeks = 12 means this week plus the 11 before it.
	 */
	public static Instant historyStart(Instant now, ZoneId zone, int weeks) {
		LocalDate oldestMonday = weekStart(now, zone).minusWeeks(weeks - 1L);
		return startOf(oldestMonday, zone);
	}

	/**
	 * Groups finished workouts into the user's Monday-Sunday weeks, by the time each workout started.
	 * Newest week first, and newest workout first within a week. Weeks without workouts don't appear.
	 */
	public static List<HistoryWeek> groupByWeek(List<HistorySession> sessions, ZoneId zone) {
		List<HistorySession> newestFirst = sessions.stream()
			.sorted(Comparator.comparing(HistorySession::startedAt).reversed())
			.toList();
		// A LinkedHashMap keeps the weeks in the order they are first seen, which is newest first.
		Map<LocalDate, List<HistorySession>> byWeek = new LinkedHashMap<>();
		for (HistorySession session : newestFirst) {
			byWeek.computeIfAbsent(weekStart(session.startedAt(), zone), monday -> new ArrayList<>()).add(session);
		}
		return byWeek.entrySet().stream()
			.map(week -> new HistoryWeek(week.getKey(), List.copyOf(week.getValue())))
			.toList();
	}

}
