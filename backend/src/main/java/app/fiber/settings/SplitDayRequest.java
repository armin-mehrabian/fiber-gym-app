package app.fiber.settings;

import java.util.List;

/** One day of a CUSTOM split, as sent by the client: a name and the regions it trains. */
public record SplitDayRequest(String name, List<String> regions) {
}
