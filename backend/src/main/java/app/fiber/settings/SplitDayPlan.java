package app.fiber.settings;

import java.util.List;

/**
 * One day of a split before it is saved: its name and the regions it trains.
 * Built by {@link SplitPresets}, or from the days a user sends for a CUSTOM split.
 */
public record SplitDayPlan(String name, List<String> regions) {

	public SplitDayPlan {
		regions = List.copyOf(regions);
	}

}
