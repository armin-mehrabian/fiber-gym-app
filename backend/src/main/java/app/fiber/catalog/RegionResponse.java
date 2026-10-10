package app.fiber.catalog;

/**
 * One item of GET /api/regions, e.g. {"id":"lats_upper","name":"Upper lats","group":"back","view":"back","order":30}.
 */
public record RegionResponse(String id, String name, String group, String view, int order) {

	static RegionResponse from(Region region) {
		return new RegionResponse(region.getId(), region.getName(), region.getMuscleGroup(), region.getView(),
			region.getSortOrder());
	}

}
