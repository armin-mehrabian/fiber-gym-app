package app.fiber.recommend;

import java.util.Set;
import java.util.UUID;

/** One day of the user's split, as the recommender sees it: its place in the rotation and its regions. */
public record SplitDayOption(UUID id, int position, String name, Set<String> regionIds) {
}
