package app.fiber.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A piece of gym equipment, e.g. cables (table equipment). Reference data from the seed loader.
 *
 * position is the item's index in equipment.json (0 = first); the equipment list is sorted by it.
 */
@Entity
@Table(name = "equipment")
public class Equipment {

	@Id
	private String id;

	private String name;

	private int position;

	protected Equipment() {
		// for JPA
	}

	public Equipment(String id, String name, int position) {
		this.id = id;
		this.name = name;
		this.position = position;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getPosition() {
		return position;
	}

}
