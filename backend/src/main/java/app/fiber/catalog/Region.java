package app.fiber.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A trainable muscle region, e.g. lats_upper (table region).
 * Reference data: written only by the seed loader, read by everyone.
 */
@Entity
@Table(name = "region")
public class Region {

	@Id
	private String id;

	private String name;

	// "group" is a reserved word in SQL, so the column is called muscle_group.
	@Column(name = "muscle_group")
	private String muscleGroup;

	// Which side of the body figure shows it: front, back or both.
	private String view;

	@Column(name = "sort_order")
	private int sortOrder;

	protected Region() {
		// for JPA
	}

	public Region(String id, String name, String muscleGroup, String view, int sortOrder) {
		this.id = id;
		this.name = name;
		this.muscleGroup = muscleGroup;
		this.view = view;
		this.sortOrder = sortOrder;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getMuscleGroup() {
		return muscleGroup;
	}

	public String getView() {
		return view;
	}

	public int getSortOrder() {
		return sortOrder;
	}

}
