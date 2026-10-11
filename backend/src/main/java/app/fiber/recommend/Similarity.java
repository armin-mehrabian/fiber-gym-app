package app.fiber.recommend;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How alike two exercises are, by the regions they emphasise: each exercise gives every region a
 * weight (1 primary, 0.5 secondary, 0 otherwise), and
 * similarity = sum of the smaller weights / sum of the larger weights, over all their regions.
 * The same regions with the same roles = 1; no region in common = 0.
 *
 * Pure Java, no Spring, no database.
 */
public final class Similarity {

	public static final int MAX_ALTERNATIVES = 5;

	private Similarity() {
	}

	public static double similarity(Candidate a, Candidate b) {
		Map<String, Double> weightsA = a.regionWeights();
		Map<String, Double> weightsB = b.regionWeights();
		Set<String> regions = new HashSet<>(weightsA.keySet());
		regions.addAll(weightsB.keySet());
		double sumOfMin = 0;
		double sumOfMax = 0;
		for (String region : regions) {
			double weightA = weightsA.getOrDefault(region, 0.0);
			double weightB = weightsB.getOrDefault(region, 0.0);
			sumOfMin += Math.min(weightA, weightB);
			sumOfMax += Math.max(weightA, weightB);
		}
		return sumOfMax == 0 ? 0 : sumOfMin / sumOfMax;
	}

	/**
	 * The top 5 swaps for {@code current} among {@code candidates} (the current exercise itself is left out):
	 * only exercises with something in common, most similar first, ties by id.
	 */
	public static List<Alternative> alternatives(Candidate current, List<Candidate> candidates) {
		return candidates.stream()
			.filter(candidate -> !candidate.id().equals(current.id()))
			.filter(candidate -> similarity(current, candidate) > 0)
			.map(candidate -> new Alternative(candidate.id(), round(similarity(current, candidate))))
			.sorted(Comparator.comparingDouble(Alternative::similarity).reversed()
				.thenComparing(Alternative::exerciseId))
			.limit(MAX_ALTERNATIVES)
			.toList();
	}

	/** 2 decimals, e.g. 0.6666 -> 0.67. Sorting uses this rounded value, so equal-looking scores sort by id. */
	static double round(double value) {
		return Math.round(value * 100) / 100.0;
	}

}
