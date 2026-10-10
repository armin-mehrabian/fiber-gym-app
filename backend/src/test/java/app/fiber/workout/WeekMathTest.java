package app.fiber.workout;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The Monday-Sunday week in the user's timezone: boundaries, timezones and clock changes.
 * 2026-10-05 is a Monday.
 */
class WeekMathTest {

	private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
	private static final ZoneId VANCOUVER = ZoneId.of("America/Vancouver");
	private static final ZoneId AUCKLAND = ZoneId.of("Pacific/Auckland");
	private static final ZoneId UTC = ZoneOffset.UTC;

	// ---- weekStart(LocalDate) ----

	@Test
	void everyDayFromMondayToSundayBelongsToThatMonday() {
		LocalDate monday = LocalDate.of(2026, 10, 5);
		for (int day = 0; day < 7; day++) {
			assertThat(WeekMath.weekStart(monday.plusDays(day))).as("day " + day).isEqualTo(monday);
		}
	}

	@Test
	void theNextMondayStartsANewWeek() {
		assertThat(WeekMath.weekStart(LocalDate.of(2026, 10, 12))).isEqualTo(LocalDate.of(2026, 10, 12));
	}

	@Test
	void weeksCanSpanTwoYears() {
		// Thursday 1 January 2026 belongs to the week of Monday 29 December 2025.
		assertThat(WeekMath.weekStart(LocalDate.of(2026, 1, 1))).isEqualTo(LocalDate.of(2025, 12, 29));
	}

	// ---- weekStart(Instant, zone): the boundaries in local time ----

	@Test
	void mondayAtMidnightLocalIsTheFirstMomentOfTheWeek() {
		Instant mondayMidnight = local("2026-10-05T00:00:00", BERLIN);

		assertThat(WeekMath.weekStart(mondayMidnight, BERLIN)).isEqualTo(LocalDate.of(2026, 10, 5));
	}

	@Test
	void sundayAt2359LocalIsStillTheOldWeek() {
		Instant sundayLate = local("2026-10-11T23:59:59", BERLIN);

		assertThat(WeekMath.weekStart(sundayLate, BERLIN)).isEqualTo(LocalDate.of(2026, 10, 5));
	}

	@Test
	void oneSecondBeforeMondayMidnightIsThePreviousWeek() {
		Instant justBefore = local("2026-10-05T00:00:00", BERLIN).minusSeconds(1);

		assertThat(WeekMath.weekStart(justBefore, BERLIN)).isEqualTo(LocalDate.of(2026, 9, 28));
	}

	// ---- the same moment in different timezones ----

	@Test
	void aUtcMomentOnSundayEveningIsAlreadyMondayFurtherEast() {
		// Sunday 11 Oct 22:30 UTC = Monday 12 Oct 00:30 in Berlin (UTC+2 in October).
		Instant moment = Instant.parse("2026-10-11T22:30:00Z");

		assertThat(WeekMath.weekStart(moment, UTC)).isEqualTo(LocalDate.of(2026, 10, 5));
		assertThat(WeekMath.weekStart(moment, BERLIN)).isEqualTo(LocalDate.of(2026, 10, 12));
	}

	@Test
	void aUtcMomentOnMondayMorningIsStillSundayFurtherWest() {
		// Monday 12 Oct 03:00 UTC = Sunday 11 Oct 20:00 in Vancouver (UTC-7 in October).
		Instant moment = Instant.parse("2026-10-12T03:00:00Z");

		assertThat(WeekMath.weekStart(moment, UTC)).isEqualTo(LocalDate.of(2026, 10, 12));
		assertThat(WeekMath.weekStart(moment, VANCOUVER)).isEqualTo(LocalDate.of(2026, 10, 5));
	}

	@Test
	void mondayJustAfterMidnightInAucklandIsSundayInVancouver() {
		Instant moment = local("2026-10-12T00:30:00", AUCKLAND);

		assertThat(WeekMath.weekStart(moment, AUCKLAND)).isEqualTo(LocalDate.of(2026, 10, 12));
		assertThat(WeekMath.weekStart(moment, VANCOUVER)).isEqualTo(LocalDate.of(2026, 10, 5));
	}

	// ---- startOf: Monday 00:00 local as an exact moment ----

	@Test
	void startOfIsMondayMidnightInTheUsersTimezone() {
		LocalDate monday = LocalDate.of(2026, 10, 5);

		assertThat(WeekMath.startOf(monday, BERLIN)).isEqualTo(Instant.parse("2026-10-04T22:00:00Z"));
		assertThat(WeekMath.startOf(monday, VANCOUVER)).isEqualTo(Instant.parse("2026-10-05T07:00:00Z"));
		assertThat(WeekMath.startOf(monday, UTC)).isEqualTo(Instant.parse("2026-10-05T00:00:00Z"));
	}

	// ---- daylight saving time ----

	@Test
	void springForwardWeekIsOneHourShorterButKeepsItsBoundaries() {
		// Europe moves the clocks forward on Sunday 29 March 2026, so that week has 167 hours.
		LocalDate monday = LocalDate.of(2026, 3, 23);
		LocalDate nextMonday = LocalDate.of(2026, 3, 30);

		assertThat(WeekMath.startOf(monday, BERLIN)).isEqualTo(Instant.parse("2026-03-22T23:00:00Z")); // UTC+1
		assertThat(WeekMath.startOf(nextMonday, BERLIN)).isEqualTo(Instant.parse("2026-03-29T22:00:00Z")); // UTC+2
		assertThat(WeekMath.weekStart(local("2026-03-29T23:59:00", BERLIN), BERLIN)).isEqualTo(monday);
		assertThat(WeekMath.weekStart(local("2026-03-30T00:00:00", BERLIN), BERLIN)).isEqualTo(nextMonday);
	}

	@Test
	void fallBackWeekIsOneHourLongerButKeepsItsBoundaries() {
		// Los Angeles moves the clocks back on Sunday 1 November 2026, so that week has 169 hours.
		ZoneId losAngeles = ZoneId.of("America/Los_Angeles");
		LocalDate monday = LocalDate.of(2026, 10, 26);
		LocalDate nextMonday = LocalDate.of(2026, 11, 2);

		assertThat(WeekMath.startOf(monday, losAngeles)).isEqualTo(Instant.parse("2026-10-26T07:00:00Z")); // UTC-7
		assertThat(WeekMath.startOf(nextMonday, losAngeles)).isEqualTo(Instant.parse("2026-11-02T08:00:00Z")); // UTC-8
		assertThat(WeekMath.weekStart(Instant.parse("2026-11-02T07:59:59Z"), losAngeles)).isEqualTo(monday);
		assertThat(WeekMath.weekStart(Instant.parse("2026-11-02T08:00:00Z"), losAngeles)).isEqualTo(nextMonday);
	}

	@Test
	void whenMidnightIsSkippedTheWeekStartsAtTheFirstRealMoment() {
		// Chile moves its clocks forward at midnight (00:00 -> 01:00). On Sunday 6 September 2026
		// there is no 00:00 in Santiago, so that day starts at 01:00 local time.
		ZoneId santiago = ZoneId.of("America/Santiago");
		LocalDate day = LocalDate.of(2026, 9, 6);

		Instant start = day.atStartOfDay(santiago).toInstant();

		assertThat(start.atZone(santiago).toLocalTime().getHour()).isEqualTo(1);
		// startOf uses the same rule, so a Monday with a skipped midnight still gets a valid start.
		assertThat(WeekMath.startOf(day, santiago)).isEqualTo(start);
	}

	// ---- historyStart ----

	@Test
	void historyOfOneWeekStartsThisMonday() {
		Instant saturday = local("2026-10-10T15:00:00", BERLIN);

		assertThat(WeekMath.historyStart(saturday, BERLIN, 1)).isEqualTo(local("2026-10-05T00:00:00", BERLIN));
	}

	@Test
	void historyOfTwelveWeeksStartsElevenMondaysEarlier() {
		Instant saturday = local("2026-10-10T15:00:00", BERLIN);

		assertThat(WeekMath.historyStart(saturday, BERLIN, 12)).isEqualTo(local("2026-07-20T00:00:00", BERLIN));
	}

	@Test
	void historyStartUsesTheUsersOwnWeek() {
		// Monday 12 Oct 03:00 UTC: a new week in UTC, but still Sunday 11 Oct in Vancouver.
		Instant now = Instant.parse("2026-10-12T03:00:00Z");

		assertThat(WeekMath.historyStart(now, UTC, 1)).isEqualTo(Instant.parse("2026-10-12T00:00:00Z"));
		assertThat(WeekMath.historyStart(now, VANCOUVER, 1)).isEqualTo(local("2026-10-05T00:00:00", VANCOUVER));
	}

	// ---- groupByWeek ----

	@Test
	void noSessionsGiveNoWeeks() {
		assertThat(WeekMath.groupByWeek(List.of(), BERLIN)).isEmpty();
	}

	@Test
	void sessionsAreGroupedNewestWeekFirstAndEmptyWeeksAreLeftOut() {
		HistorySession oldWeek = session("2026-09-21T18:00:00", BERLIN); // week of 21 Sep
		HistorySession mondayMorning = session("2026-10-05T00:00:00", BERLIN); // week of 5 Oct
		HistorySession sundayNight = session("2026-10-11T23:59:00", BERLIN); // still week of 5 Oct
		HistorySession nextWeek = session("2026-10-12T07:00:00", BERLIN); // week of 12 Oct

		// Given in a mixed order on purpose.
		List<HistoryWeek> weeks = WeekMath.groupByWeek(List.of(mondayMorning, oldWeek, nextWeek, sundayNight), BERLIN);

		assertThat(weeks).extracting(HistoryWeek::weekStart)
			.containsExactly(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 9, 21));
		assertThat(weeks.get(0).sessions()).containsExactly(nextWeek);
		assertThat(weeks.get(1).sessions()).containsExactly(sundayNight, mondayMorning); // newest first
		assertThat(weeks.get(2).sessions()).containsExactly(oldWeek);
	}

	@Test
	void theSameSessionLandsInDifferentWeeksForDifferentTimezones() {
		HistorySession session = session("2026-10-12T00:30:00", AUCKLAND); // Sunday evening in Vancouver

		assertThat(WeekMath.groupByWeek(List.of(session), AUCKLAND).get(0).weekStart())
			.isEqualTo(LocalDate.of(2026, 10, 12));
		assertThat(WeekMath.groupByWeek(List.of(session), VANCOUVER).get(0).weekStart())
			.isEqualTo(LocalDate.of(2026, 10, 5));
	}

	@Test
	void sessionsAreGroupedByWhenTheyStartedNotWhenTheyFinished() {
		// Started Sunday 23:30, finished Monday 00:45: it belongs to the week it started in.
		Instant start = local("2026-10-11T23:30:00", BERLIN);
		HistorySession lateSession = new HistorySession(UUID.randomUUID(), "Late", start,
			local("2026-10-12T00:45:00", BERLIN), 1, 3);

		assertThat(WeekMath.groupByWeek(List.of(lateSession), BERLIN).get(0).weekStart())
			.isEqualTo(LocalDate.of(2026, 10, 5));
	}

	private static HistorySession session(String startLocal, ZoneId zone) {
		Instant start = local(startLocal, zone);
		return new HistorySession(UUID.randomUUID(), "Workout", start, start.plusSeconds(3600), 2, 6);
	}

	private static Instant local(String dateTime, ZoneId zone) {
		return LocalDateTime.parse(dateTime).atZone(zone).toInstant();
	}

}
