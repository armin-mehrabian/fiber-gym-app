package app.fiber.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProgressionRuleTest {

	// ---- no history ----

	@Test
	void withoutHistoryThereIsNoWeightAndTheTargetIsTheTopOfTheRange() {
		Suggestion suggestion = ProgressionRule.suggest("horizontal_push", 8, 12, List.of());

		assertThat(suggestion).isEqualTo(new Suggestion(null, 12, SuggestionReason.NO_HISTORY));
	}

	// ---- every set hit the top: more weight ----

	@Test
	void allSetsAtTheTopAddTwoAndAHalfKgForUpperBodyAndResetToTheBottom() {
		// The example from the rules: 20 kg x 12, 12, 12 with 10-12 -> 22.5 kg x 10.
		Suggestion suggestion = ProgressionRule.suggest("horizontal_pull", 10, 12, sets("20", 12, 12, 12));

		assertThat(suggestion).isEqualTo(new Suggestion(kg("22.50"), 10, SuggestionReason.INCREASE));
	}

	@ParameterizedTest
	@ValueSource(strings = { "squat", "hinge", "lunge", "calf", "knee_extension", "knee_flexion", "hip_abduction",
		"hip_adduction" })
	void lowerBodyPatternsGoUpByFiveKg(String pattern) {
		Suggestion suggestion = ProgressionRule.suggest(pattern, 6, 10, sets("100", 10, 10, 10));

		assertThat(suggestion).isEqualTo(new Suggestion(kg("105.00"), 6, SuggestionReason.INCREASE));
	}

	@ParameterizedTest
	@ValueSource(strings = { "horizontal_push", "vertical_pull", "elbow_flexion", "lateral_raise", "core_flexion",
		"shrug", "unknown_pattern" })
	void everyOtherPatternGoesUpByTwoAndAHalfKg(String pattern) {
		assertThat(ProgressionRule.increment(pattern)).isEqualByComparingTo("2.5");
	}

	@Test
	void moreRepsThanTheTopAlsoCountsAsReachingIt() {
		Suggestion suggestion = ProgressionRule.suggest("fly", 10, 15, sets("10", 15, 16, 18));

		assertThat(suggestion.reason()).isEqualTo(SuggestionReason.INCREASE);
		assertThat(suggestion.weightKg()).isEqualByComparingTo("12.5");
	}

	@Test
	void theIncreaseStartsFromTheHeaviestSetAndKeepsTwoDecimals() {
		// Odd weights stay exact: 21.25 + 2.5 = 23.75, never rounded to a plate step.
		Suggestion suggestion = ProgressionRule.suggest("fly", 10, 12, List.of(
			new LoggedSet(kg("20"), 12), new LoggedSet(kg("21.25"), 12)));

		assertThat(suggestion.weightKg()).isEqualTo(kg("23.75"));
		assertThat(suggestion.weightKg().scale()).isEqualTo(2);
	}

	// ---- not every set hit the top: same weight, one more rep ----

	@Test
	void missingTheTopKeepsTheWeightAndAimsForOneMoreThanTheWeakestSet() {
		// The example from the rules: 30 kg x 11, 10, 9 with 8-12 -> 30 kg x 10.
		Suggestion suggestion = ProgressionRule.suggest("horizontal_push", 8, 12, sets("30", 11, 10, 9));

		assertThat(suggestion).isEqualTo(new Suggestion(kg("30.00"), 10, SuggestionReason.REPEAT));
	}

	@Test
	void oneSetShortOfTheTopIsEnoughToRepeat() {
		Suggestion suggestion = ProgressionRule.suggest("squat", 6, 10, sets("100", 10, 10, 9));

		assertThat(suggestion).isEqualTo(new Suggestion(kg("100.00"), 10, SuggestionReason.REPEAT));
	}

	@Test
	void oneRepShortOnTheWeakestSetAimsForTheTop() {
		Suggestion suggestion = ProgressionRule.suggest("fly", 10, 12, sets("10", 12, 11));

		assertThat(suggestion).isEqualTo(new Suggestion(kg("10.00"), 12, SuggestionReason.REPEAT));
	}

	@Test
	void theRepeatWeightIsTheHeaviestWorkingSet() {
		Suggestion suggestion = ProgressionRule.suggest("hinge", 6, 10, List.of(
			new LoggedSet(kg("80"), 10), new LoggedSet(kg("90"), 7), new LoggedSet(kg("85"), 8)));

		assertThat(suggestion).isEqualTo(new Suggestion(kg("90.00"), 8, SuggestionReason.REPEAT));
	}

	@Test
	void farBelowTheRangeItStillAimsForJustOneMoreRep() {
		Suggestion suggestion = ProgressionRule.suggest("horizontal_push", 8, 12, sets("50", 4, 3));

		assertThat(suggestion.reps()).isEqualTo(4);
	}

	// ---- bodyweight ----

	@Test
	void bodyweightAimsForOneMoreRepThanTheBestSet() {
		Suggestion suggestion = ProgressionRule.suggest("horizontal_push", 8, 20, sets("0", 15, 12, 10));

		assertThat(suggestion).isEqualTo(new Suggestion(kg("0.00"), 16, SuggestionReason.MORE_REPS));
	}

	@Test
	void bodyweightAboveTheTopOfTheRangeKeepsAddingReps() {
		// No weight to add, so reps keep going up past the range.
		Suggestion suggestion = ProgressionRule.suggest("horizontal_push", 8, 20, sets("0", 20, 20, 20));

		assertThat(suggestion).isEqualTo(new Suggestion(kg("0.00"), 21, SuggestionReason.MORE_REPS));
	}

	@Test
	void bodyweightRepsStopAtTheMostThatCanBeLogged() {
		assertThat(ProgressionRule.suggest("core_flexion", 10, 20, sets("0", 100)).reps()).isEqualTo(100);
		assertThat(ProgressionRule.suggest("core_flexion", 10, 20, sets("0", 99)).reps()).isEqualTo(100);
	}

	// ---- helpers ----

	private static BigDecimal kg(String value) {
		return new BigDecimal(value).setScale(2);
	}

	private static List<LoggedSet> sets(String weightKg, Integer... reps) {
		return Arrays.stream(reps).map(r -> new LoggedSet(kg(weightKg), r)).toList();
	}

}
