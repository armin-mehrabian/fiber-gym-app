package app.fiber.coverage;

/** One exercise on one day that helped a region, e.g. {"exerciseName": "Face pull", "day": "Tue", "sets": 3.0}. */
public record ContributionResponse(String exerciseName, String day, double sets) {
}
