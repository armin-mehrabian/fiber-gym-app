package app.fiber.coverage;

import app.fiber.catalog.RegionRole;
import app.fiber.common.BadRequestException;
import app.fiber.common.NotFoundException;
import app.fiber.settings.RegionTarget;
import app.fiber.settings.RegionTargetRepository;
import app.fiber.settings.UserSettings;
import app.fiber.settings.UserSettingsRepository;
import app.fiber.workout.WeekMath;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds GET /api/coverage/week: loads the user's sets and targets, lets the pure calculators do
 * the maths, and maps the result to the API shape.
 *
 * A workout belongs to the week its start time falls in, in the user's timezone (the same rule as
 * the history list). Recovery looks at the last 48 hours from now, whatever week is asked for.
 */
@Service
public class CoverageService {

	// The whole week asked for (Monday to Sunday) must lie in the years 0001-9999, so every date in the
	// answer has a 4-digit year and every moment can be stored as a database timestamp.
	// 0001-01-01 is a Monday, so its week is the first one allowed.
	static final LocalDate FIRST_ALLOWED_DATE = LocalDate.of(1, 1, 1);
	// The week of 9999-12-31 ends in the year 10000, so the last allowed date is the Sunday before
	// that week starts: 9999-12-26.
	static final LocalDate LAST_ALLOWED_DATE = WeekMath.weekStart(LocalDate.of(9999, 12, 31)).minusDays(1);

	private final CoverageRepository coverageRepository;
	private final RegionTargetRepository targetRepository;
	private final UserSettingsRepository settingsRepository;

	public CoverageService(CoverageRepository coverageRepository, RegionTargetRepository targetRepository,
			UserSettingsRepository settingsRepository) {
		this.coverageRepository = coverageRepository;
		this.targetRepository = targetRepository;
		this.settingsRepository = settingsRepository;
	}

	/** The week containing {@code date}; null means today in the user's timezone. */
	@Transactional(readOnly = true)
	public CoverageWeekResponse week(UUID userId, LocalDate date) {
		checkDateInRange(date);
		ZoneId zone = userTimezone(userId);
		Instant now = Instant.now();
		LocalDate monday = WeekMath.weekStart(date != null ? date : now.atZone(zone).toLocalDate());
		Instant from = WeekMath.startOf(monday, zone);
		Instant to = WeekMath.startOf(monday.plusWeeks(1), zone);

		CoverageResult coverage = CoverageCalculator.calculate(targets(userId),
			countedSets(coverageRepository.findCountedSetRegions(userId, from, to), zone));
		Set<String> recovering = RecoveryCalculator.recoveringRegions(recentSessions(userId, now), now);
		List<CoverageDayResponse> days = days(monday, coverageRepository.findFinishedStartTimes(userId, from, to),
			zone);

		List<RegionCoverageResponse> regions = coverage.regions().stream()
			.map(region -> toResponse(region, recovering.contains(region.regionId())))
			.toList();
		return new CoverageWeekResponse(monday, monday.plusDays(6), coverage.weekPercent(), days, regions);
	}

	/** Checked on the date itself, before any week maths, which could fail on extreme dates. */
	private static void checkDateInRange(LocalDate date) {
		if (date != null && (date.isBefore(FIRST_ALLOWED_DATE) || date.isAfter(LAST_ALLOWED_DATE))) {
			throw new BadRequestException("Invalid input",
				Map.of("date", "its week must lie between 0001-01-01 and 9999-12-31"));
		}
	}

	/** The user's weekly target per region, in region order. */
	private Map<String, Integer> targets(UUID userId) {
		Map<String, Integer> targets = new LinkedHashMap<>();
		for (RegionTarget target : targetRepository.findByUserInRegionOrder(userId)) {
			targets.put(target.getRegionId(), target.getWeeklySets());
		}
		return targets;
	}

	/**
	 * Turns the (set, region) rows back into one CountedSet per set, with its primary and secondary
	 * regions. The day is the day the workout started, in the user's timezone.
	 */
	static List<CountedSet> countedSets(List<CountedSetRegion> rows, ZoneId zone) {
		Map<UUID, List<CountedSetRegion>> rowsBySet = new LinkedHashMap<>();
		for (CountedSetRegion row : rows) {
			rowsBySet.computeIfAbsent(row.setId(), id -> new ArrayList<>()).add(row);
		}
		List<CountedSet> sets = new ArrayList<>();
		for (List<CountedSetRegion> setRows : rowsBySet.values()) {
			CountedSetRegion first = setRows.getFirst();
			sets.add(new CountedSet(
				first.exerciseName(),
				first.sessionStartedAt().atZone(zone).toLocalDate(),
				regionIds(setRows, RegionRole.PRIMARY),
				regionIds(setRows, RegionRole.SECONDARY)));
		}
		return sets;
	}

	private static List<String> regionIds(List<CountedSetRegion> rows, RegionRole role) {
		return rows.stream().filter(row -> row.role() == role).map(CountedSetRegion::regionId).toList();
	}

	/** The workouts that finished in the last 48 hours, with the effective sets each one gave every region. */
	private List<RecentSession> recentSessions(UUID userId, Instant now) {
		List<SessionRegionSets> rows = coverageRepository.findSessionRegionSetsFinishedSince(userId,
			RecoveryCalculator.windowStart(now));
		return recentSessions(rows);
	}

	static List<RecentSession> recentSessions(List<SessionRegionSets> rows) {
		Map<UUID, Instant> finishedAt = new LinkedHashMap<>();
		Map<UUID, Map<String, Double>> effectiveBySession = new HashMap<>();
		for (SessionRegionSets row : rows) {
			finishedAt.put(row.sessionId(), row.finishedAt());
			double effective = row.sets() * CoverageCalculator.creditFor(row.role());
			effectiveBySession.computeIfAbsent(row.sessionId(), id -> new HashMap<>())
				.merge(row.regionId(), effective, Double::sum);
		}
		return finishedAt.entrySet().stream()
			.map(session -> new RecentSession(session.getValue(), effectiveBySession.get(session.getKey())))
			.toList();
	}

	/** Monday to Sunday, each with the number of finished workouts that started that day (user's timezone). */
	static List<CoverageDayResponse> days(LocalDate monday, List<Instant> sessionStarts, ZoneId zone) {
		List<CoverageDayResponse> days = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			LocalDate date = monday.plusDays(i);
			int sessions = (int) sessionStarts.stream()
				.filter(start -> start.atZone(zone).toLocalDate().equals(date))
				.count();
			days.add(new CoverageDayResponse(date, sessions));
		}
		return days;
	}

	private static RegionCoverageResponse toResponse(RegionCoverage region, boolean recovering) {
		List<ContributionResponse> contributions = region.contributions().stream()
			.map(contribution -> new ContributionResponse(contribution.exerciseName(), shortDayName(contribution.day()),
				contribution.sets()))
			.toList();
		return new RegionCoverageResponse(region.regionId(), region.effective(), region.target(), region.coverage(),
			recovering, contributions);
	}

	/** "Mon" ... "Sun". Always English, whatever the server's language setting is. */
	static String shortDayName(LocalDate day) {
		return day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
	}

	private ZoneId userTimezone(UUID userId) {
		return settingsRepository.findById(userId)
			.map(UserSettings::getTimezone)
			.map(ZoneId::of)
			.orElseThrow(() -> new NotFoundException("Settings not found"));
	}

}
