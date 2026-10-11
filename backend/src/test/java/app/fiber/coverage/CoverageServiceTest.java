package app.fiber.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import app.fiber.catalog.RegionRole;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The service's mapping steps between database rows and the calculators, without a database.
 * The whole endpoint is tested end to end in CoverageIntegrationTest.
 */
class CoverageServiceTest {

	private static final ZoneId VANCOUVER = ZoneId.of("America/Vancouver");
	private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

	@Test
	void rowsOfTheSameSetBecomeOneCountedSetWithItsRegions() {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		Instant tuesdayEvening = Instant.parse("2026-10-07T01:30:00Z"); // Tuesday 18:30 in Vancouver
		List<CountedSetRegion> rows = List.of(
			new CountedSetRegion(first, "Incline press", tuesdayEvening, "chest_upper", RegionRole.PRIMARY),
			new CountedSetRegion(first, "Incline press", tuesdayEvening, "delt_front", RegionRole.SECONDARY),
			new CountedSetRegion(second, "Incline press", tuesdayEvening, "chest_upper", RegionRole.PRIMARY),
			new CountedSetRegion(first, "Incline press", tuesdayEvening, "chest_mid", RegionRole.PRIMARY),
			new CountedSetRegion(second, "Incline press", tuesdayEvening, "chest_mid", RegionRole.PRIMARY),
			new CountedSetRegion(second, "Incline press", tuesdayEvening, "delt_front", RegionRole.SECONDARY));

		List<CountedSet> sets = CoverageService.countedSets(rows, VANCOUVER);

		CountedSet expected = new CountedSet("Incline press", MONDAY.plusDays(1), List.of("chest_upper", "chest_mid"),
			List.of("delt_front"));
		assertThat(sets).containsExactly(expected, expected);
	}

	@Test
	void recentSessionsWeighPrimaryOneAndSecondaryHalfPerWorkout() {
		UUID workout = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		Instant finished = Instant.parse("2026-10-09T10:00:00Z");
		List<SessionRegionSets> rows = List.of(
			// 2 sets of an exercise with chest_mid as primary, 2 of one with it as secondary: 2 + 1 = 3
			new SessionRegionSets(workout, finished, "chest_mid", RegionRole.PRIMARY, 2),
			new SessionRegionSets(workout, finished, "chest_mid", RegionRole.SECONDARY, 2),
			new SessionRegionSets(workout, finished, "delt_front", RegionRole.SECONDARY, 3),
			new SessionRegionSets(other, finished.plusSeconds(60), "chest_mid", RegionRole.PRIMARY, 1));

		List<RecentSession> sessions = CoverageService.recentSessions(rows);

		assertThat(sessions).containsExactly(
			new RecentSession(finished, Map.of("chest_mid", 3.0, "delt_front", 1.5)),
			new RecentSession(finished.plusSeconds(60), Map.of("chest_mid", 1.0)));
	}

	@Test
	void daysAreMondayToSundayWithWorkoutsCountedInTheUsersTimezone() {
		List<Instant> starts = List.of(
			Instant.parse("2026-10-06T07:30:00Z"), // Tuesday 00:30 in Vancouver
			Instant.parse("2026-10-06T06:30:00Z"), // still Monday 23:30 in Vancouver
			Instant.parse("2026-10-06T20:00:00Z")); // Tuesday 13:00

		List<CoverageDayResponse> days = CoverageService.days(MONDAY, starts, VANCOUVER);

		assertThat(days).extracting(CoverageDayResponse::date)
			.containsExactly(MONDAY, MONDAY.plusDays(1), MONDAY.plusDays(2), MONDAY.plusDays(3), MONDAY.plusDays(4),
				MONDAY.plusDays(5), MONDAY.plusDays(6));
		assertThat(days).extracting(CoverageDayResponse::sessions).containsExactly(1, 2, 0, 0, 0, 0, 0);
	}

	@Test
	void dayNamesAreShortEnglishNames() {
		assertThat(CoverageService.shortDayName(MONDAY)).isEqualTo("Mon");
		assertThat(CoverageService.shortDayName(MONDAY.plusDays(3))).isEqualTo("Thu");
		assertThat(CoverageService.shortDayName(MONDAY.plusDays(6))).isEqualTo("Sun");
	}

}
