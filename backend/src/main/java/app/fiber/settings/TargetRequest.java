package app.fiber.settings;

/** One entry of PUT /api/targets. Checked by the service (region must exist, 0-30 sets). */
public record TargetRequest(String regionId, Integer weeklySets) {
}
