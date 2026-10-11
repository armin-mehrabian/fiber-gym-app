package app.fiber.recommend;

import java.util.List;
import java.util.Map;

/**
 * The recommender's answer: which split day is next, the exercise ids to do in order
 * (each with 3 working sets), and the planned overlay for the body figure
 * (region id to 1 = hit mainly, 0.5 = helped).
 */
public record Recommendation(SplitDayOption day, List<String> exerciseIds, Map<String, Double> planned) {
}
