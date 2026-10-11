package app.fiber.recommend;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An exercise the recommender may pick, with what it emphasises. primary is in library order,
 * so the first primary region is the exercise's main region.
 */
public record Candidate(String id, String pattern, List<String> primary, List<String> secondary) {

	/** A set counts fully for a primary region and half for a secondary region. */
	static final double PRIMARY_WEIGHT = 1.0;
	static final double SECONDARY_WEIGHT = 0.5;

	/** The region the exercise hits most: its first primary region (null if it has none). */
	public String mainRegion() {
		return primary.isEmpty() ? null : primary.getFirst();
	}

	/** Region id to weight: 1 for each primary region, 0.5 for each secondary region. */
	public Map<String, Double> regionWeights() {
		Map<String, Double> weights = new LinkedHashMap<>();
		secondary.forEach(region -> weights.put(region, SECONDARY_WEIGHT));
		// Primary last, so it wins if a region were ever listed in both.
		primary.forEach(region -> weights.put(region, PRIMARY_WEIGHT));
		return weights;
	}

}
