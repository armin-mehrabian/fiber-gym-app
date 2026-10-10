package app.fiber.settings;

import java.util.List;
import java.util.UUID;

/** One day of the user's split. regions are sorted in the order regions are shown. */
public record SplitDayResponse(UUID id, int position, String name, List<String> regions) {
}
