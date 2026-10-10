package app.fiber.settings;

/** One region's weekly set target. */
public record TargetResponse(String regionId, int weeklySets) {

	static TargetResponse from(RegionTarget target) {
		return new TargetResponse(target.getRegionId(), target.getWeeklySets());
	}

}
