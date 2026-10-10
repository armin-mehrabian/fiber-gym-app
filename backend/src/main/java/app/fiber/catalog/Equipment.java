package app.fiber.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A piece of gym equipment, e.g. cables (table equipment). Reference data from the seed loader.
 */
@Entity
@Table(name = "equipment")
public class Equipment {

	@Id
	private String id;

	private String name;

	protected Equipment() {
		// for JPA
	}

	public Equipment(String id, String name) {
		this.id = id;
		this.name = name;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

}
