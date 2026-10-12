package app.fiber.coverage;

/**
 * One muscle-group badge for the week, e.g. {"group": "chest", "name": "Chest", "done": false,
 * "regionsDone": 2, "regionsTotal": 3}. done = every region of the group is DONE this week.
 * Also used as-is in the API answer.
 */
public record Badge(String group, String name, boolean done, int regionsDone, int regionsTotal) {
}
