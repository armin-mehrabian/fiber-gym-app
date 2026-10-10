package app.fiber.catalog;

/**
 * One item of GET /api/equipment, e.g. {"id":"cables","name":"Cable station"}.
 */
public record EquipmentResponse(String id, String name) {

	static EquipmentResponse from(Equipment equipment) {
		return new EquipmentResponse(equipment.getId(), equipment.getName());
	}

}
