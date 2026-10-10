package app.fiber.settings;

import app.fiber.catalog.Equipment;
import app.fiber.catalog.EquipmentRepository;
import app.fiber.catalog.Region;
import app.fiber.catalog.RegionRepository;
import app.fiber.common.BadRequestException;
import app.fiber.common.NotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A user's settings, split days and weekly targets.
 * Every method takes the logged-in user's id and only ever reads or writes that user's rows.
 */
@Service
public class SettingsService {

	/** Highest weekly set target a user can pick for one region. */
	private static final int MAX_WEEKLY_SETS = 30;
	private static final int MAX_CUSTOM_DAYS = 7;
	private static final int MAX_DAY_NAME_LENGTH = 20;

	private final UserSettingsRepository settingsRepository;
	private final RegionTargetRepository targetRepository;
	private final SplitDayRepository splitDayRepository;
	private final RegionRepository regionRepository;
	private final EquipmentRepository equipmentRepository;

	public SettingsService(UserSettingsRepository settingsRepository, RegionTargetRepository targetRepository,
			SplitDayRepository splitDayRepository, RegionRepository regionRepository,
			EquipmentRepository equipmentRepository) {
		this.settingsRepository = settingsRepository;
		this.targetRepository = targetRepository;
		this.splitDayRepository = splitDayRepository;
		this.regionRepository = regionRepository;
		this.equipmentRepository = equipmentRepository;
	}

	/**
	 * Called during signup, inside the signup transaction: the settings row of a new user
	 * and one weekly target per region with its default (see DefaultTargets).
	 */
	@Transactional
	public void createDefaults(UUID userId, String timezone) {
		settingsRepository.save(new UserSettings(userId, timezone));
		List<RegionTarget> targets = regionRepository.findAll().stream()
			.map(region -> new RegionTarget(userId, region.getId(), DefaultTargets.weeklySetsFor(region.getId())))
			.toList();
		targetRepository.saveAll(targets);
	}

	@Transactional(readOnly = true)
	public boolean isOnboarded(UUID userId) {
		return findSettings(userId).isOnboarded();
	}

	// ---- settings ----

	@Transactional(readOnly = true)
	public SettingsResponse getSettings(UUID userId) {
		return toResponse(findSettings(userId), splitDayRepository.findByUserIdOrderByPositionAsc(userId));
	}

	/**
	 * Saves the settings screen and marks the user as onboarded.
	 * The split days are replaced only when they actually change, so past workouts keep their link
	 * to the day they were done on when the user just changes units or equipment.
	 */
	@Transactional
	public SettingsResponse updateSettings(UUID userId, SettingsRequest request) {
		UserSettings settings = findSettings(userId);
		SplitType splitType = SplitType.valueOf(request.splitType());

		// Collect every problem first, so the client gets them all in one answer.
		Map<String, String> errors = new LinkedHashMap<>();
		Set<String> equipment = checkEquipment(request.equipment(), errors);
		List<SplitDayPlan> plannedDays = splitType == SplitType.CUSTOM
			? checkCustomDays(request.splitDays(), errors)
			: SplitPresets.daysFor(splitType);
		if (!errors.isEmpty()) {
			throw new BadRequestException("Invalid settings", errors);
		}

		settings.update(splitType, Units.valueOf(request.units()), request.timezone(), equipment);
		List<SplitDay> days = splitDayRepository.findByUserIdOrderByPositionAsc(userId);
		if (!sameDays(days, plannedDays)) {
			days = replaceSplitDays(userId, days, plannedDays);
		}
		return toResponse(settings, days);
	}

	/** Returns the equipment ids without duplicates, or records an error if any id is unknown. */
	private Set<String> checkEquipment(List<String> requested, Map<String, String> errors) {
		Set<String> ids = new LinkedHashSet<>(requested);
		// A null id can't be looked up; it simply ends up in the "unknown" list below.
		List<String> lookup = ids.stream().filter(Objects::nonNull).toList();
		Set<String> known = equipmentRepository.findAllById(lookup).stream()
			.map(Equipment::getId)
			.collect(Collectors.toSet());
		List<String> unknown = ids.stream().filter(id -> !known.contains(id)).toList();
		if (!unknown.isEmpty()) {
			errors.put("equipment", "unknown equipment: " + String.join(", ", unknown));
		}
		return ids;
	}

	/**
	 * Checks the days of a CUSTOM split: 1-7 days, each with a name of 1-20 characters
	 * and at least one known region. Errors are keyed like "splitDays[0].name".
	 */
	private List<SplitDayPlan> checkCustomDays(List<SplitDayRequest> days, Map<String, String> errors) {
		if (days == null || days.isEmpty() || days.size() > MAX_CUSTOM_DAYS) {
			errors.put("splitDays", "a custom split needs 1 to " + MAX_CUSTOM_DAYS + " days");
			return List.of();
		}
		Set<String> knownRegions = regionSortOrder().keySet();
		List<SplitDayPlan> plans = new ArrayList<>();
		for (int i = 0; i < days.size(); i++) {
			SplitDayRequest day = days.get(i) == null ? new SplitDayRequest(null, null) : days.get(i);
			String name = day.name() == null ? "" : day.name().strip();
			List<String> regions = day.regions() == null ? List.of() : day.regions();
			List<String> unknown = regions.stream().filter(id -> !knownRegions.contains(id)).toList();

			if (name.isEmpty() || name.length() > MAX_DAY_NAME_LENGTH) {
				errors.put("splitDays[" + i + "].name", "must be 1 to " + MAX_DAY_NAME_LENGTH + " characters");
			}
			if (regions.isEmpty()) {
				errors.put("splitDays[" + i + "].regions", "needs at least 1 region");
			} else if (!unknown.isEmpty()) {
				errors.put("splitDays[" + i + "].regions", "unknown region: " + String.join(", ", unknown));
			}
			plans.add(new SplitDayPlan(name, regions.stream().distinct().toList()));
		}
		return plans;
	}

	/** True if the saved days already match the plan: same order, names and regions. */
	private static boolean sameDays(List<SplitDay> saved, List<SplitDayPlan> planned) {
		if (saved.size() != planned.size()) {
			return false;
		}
		for (int i = 0; i < saved.size(); i++) {
			SplitDay day = saved.get(i);
			SplitDayPlan plan = planned.get(i);
			if (!day.getName().equals(plan.name()) || !day.getRegionIds().equals(Set.copyOf(plan.regions()))) {
				return false;
			}
		}
		return true;
	}

	/** Deletes the user's split days and saves the planned ones, numbered from 1. */
	private List<SplitDay> replaceSplitDays(UUID userId, List<SplitDay> oldDays, List<SplitDayPlan> planned) {
		splitDayRepository.deleteAll(oldDays);
		// Write the deletes now. Positions are unique per user, and Hibernate would otherwise
		// insert the new days before deleting the old ones.
		splitDayRepository.flush();

		List<SplitDay> newDays = new ArrayList<>();
		for (int i = 0; i < planned.size(); i++) {
			SplitDayPlan plan = planned.get(i);
			newDays.add(new SplitDay(userId, i + 1, plan.name(), plan.regions()));
		}
		return splitDayRepository.saveAll(newDays);
	}

	private SettingsResponse toResponse(UserSettings settings, List<SplitDay> days) {
		Map<String, Integer> sortOrder = regionSortOrder();
		List<SplitDayResponse> dayResponses = days.stream()
			.map(day -> new SplitDayResponse(day.getId(), day.getPosition(), day.getName(),
				inRegionOrder(day.getRegionIds(), sortOrder)))
			.toList();
		List<String> equipment = settings.getEquipmentIds().stream().sorted().toList();
		return new SettingsResponse(settings.getSplitType(), settings.getUnits(), settings.getTimezone(),
			settings.isOnboarded(), equipment, dayResponses);
	}

	private static List<String> inRegionOrder(Set<String> regionIds, Map<String, Integer> sortOrder) {
		return regionIds.stream()
			.sorted(Comparator.comparing((String id) -> sortOrder.getOrDefault(id, Integer.MAX_VALUE))
				.thenComparing(Function.identity()))
			.toList();
	}

	/** region id -> its display order. A HashMap, so looking up a null id is allowed (it's just unknown). */
	private Map<String, Integer> regionSortOrder() {
		Map<String, Integer> sortOrder = new HashMap<>();
		for (Region region : regionRepository.findAll()) {
			sortOrder.put(region.getId(), region.getSortOrder());
		}
		return sortOrder;
	}

	private UserSettings findSettings(UUID userId) {
		return settingsRepository.findById(userId)
			.orElseThrow(() -> new NotFoundException("Settings not found"));
	}

	// ---- weekly targets ----

	@Transactional(readOnly = true)
	public List<TargetResponse> getTargets(UUID userId) {
		return targetRepository.findByUserInRegionOrder(userId).stream().map(TargetResponse::from).toList();
	}

	/**
	 * Changes the targets that are sent (any subset) and returns the full list.
	 * Nothing is saved if any entry is invalid. Errors are keyed by region id.
	 */
	@Transactional
	public List<TargetResponse> updateTargets(UUID userId, List<TargetRequest> changes) {
		List<RegionTarget> targets = targetRepository.findByUserInRegionOrder(userId);
		Map<String, RegionTarget> byRegion = targets.stream()
			.collect(Collectors.toMap(RegionTarget::getRegionId, Function.identity()));

		Map<String, String> errors = new LinkedHashMap<>();
		for (TargetRequest change : changes) {
			if (change == null || change.regionId() == null) {
				errors.put("regionId", "is required");
			} else if (!byRegion.containsKey(change.regionId())) {
				errors.put(change.regionId(), "unknown region");
			} else if (change.weeklySets() == null || change.weeklySets() < 0
					|| change.weeklySets() > MAX_WEEKLY_SETS) {
				errors.put(change.regionId(), "must be between 0 and " + MAX_WEEKLY_SETS);
			}
		}
		if (!errors.isEmpty()) {
			throw new BadRequestException("Invalid targets", errors);
		}

		for (TargetRequest change : changes) {
			byRegion.get(change.regionId()).setWeeklySets(change.weeklySets());
		}
		return targets.stream().map(TargetResponse::from).toList();
	}

}
