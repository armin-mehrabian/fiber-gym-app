package app.fiber.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RecommenderTest {

	private static final SplitDayOption PUSH = day(1, "Push", "chest_upper", "chest_mid", "delt_front");
	private static final SplitDayOption PULL = day(2, "Pull", "lats_upper", "lats_lower", "biceps_long");
	private static final SplitDayOption LEGS = day(3, "Legs", "quads_vasti", "hamstrings");
	private static final List<SplitDayOption> PPL = List.of(PUSH, PULL, LEGS);

	@Nested
	class WhichDay {

		@Test
		void withoutHistoryItIsTheFirstDay() {
			assertThat(Recommender.nextDay(PPL, null)).isEqualTo(PUSH);
		}

		@Test
		void itIsTheDayAfterTheLastWorkoutsDay() {
			assertThat(Recommender.nextDay(PPL, PUSH.id())).isEqualTo(PULL);
			assertThat(Recommender.nextDay(PPL, PULL.id())).isEqualTo(LEGS);
		}

		@Test
		void afterTheLastDayItWrapsAroundToTheFirst() {
			assertThat(Recommender.nextDay(PPL, LEGS.id())).isEqualTo(PUSH);
		}

		@Test
		void theOrderComesFromThePositionsNotTheList() {
			assertThat(Recommender.nextDay(List.of(LEGS, PUSH, PULL), null)).isEqualTo(PUSH);
			assertThat(Recommender.nextDay(List.of(LEGS, PUSH, PULL), PUSH.id())).isEqualTo(PULL);
		}

		@Test
		void aLastWorkoutWithoutAKnownSplitDayStartsAtTheFirstDay() {
			assertThat(Recommender.nextDay(PPL, UUID.randomUUID())).isEqualTo(PUSH);
		}

		@Test
		void aOneDaySplitIsAlwaysThatDay() {
			SplitDayOption fullBody = day(1, "Full body", "chest_mid");

			assertThat(Recommender.nextDay(List.of(fullBody), fullBody.id())).isEqualTo(fullBody);
		}

		@Test
		void noSplitDaysIsAProgrammingError() {
			assertThatThrownBy(() -> Recommender.nextDay(List.of(), null))
				.isInstanceOf(IllegalArgumentException.class);
		}

	}

	@Nested
	class Gaps {

		@Test
		void theGapIsTargetMinusEffectiveNeverBelowZero() {
			Map<String, Double> gaps = Recommender.gaps(Set.of("chest_upper", "chest_mid", "delt_front"),
				Map.of("chest_upper", 6, "chest_mid", 6, "delt_front", 4),
				Map.of("chest_upper", 1.5, "chest_mid", 9.0), Set.of());

			assertThat(gaps).containsExactlyInAnyOrderEntriesOf(
				Map.of("chest_upper", 4.5, "chest_mid", 0.0, "delt_front", 4.0));
		}

		@Test
		void recoveringRegionsAndRegionsOfOtherDaysHaveNoGap() {
			Map<String, Double> gaps = Recommender.gaps(Set.of("chest_upper", "chest_mid"),
				Map.of("chest_upper", 6, "chest_mid", 6, "lats_lower", 6), Map.of(), Set.of("chest_mid"));

			assertThat(gaps).containsOnlyKeys("chest_upper");
		}

		@Test
		void aRegionWithoutATargetHasAGapOfZero() {
			assertThat(Recommender.gaps(Set.of("calves"), Map.of(), Map.of(), Set.of()))
				.containsExactlyEntriesOf(Map.of("calves", 0.0));
		}

	}

	@Nested
	class Candidates {

		@Test
		void anExerciseNeedsAPrimaryRegionOfTheDayThatIsNotRecovering() {
			Candidate press = exercise("press", "push", List.of("chest_mid"), List.of("delt_front"));
			Candidate row = exercise("row", "pull", List.of("lats_lower"), List.of("chest_mid"));
			Candidate incline = exercise("incline", "push", List.of("chest_upper", "chest_mid"), List.of());
			Candidate pushUp = exercise("push_up", "push", List.of("chest_mid"), List.of());

			List<Candidate> candidates = Recommender.candidates(Set.of("chest_upper", "chest_mid", "delt_front"),
				Set.of("chest_mid"), List.of(press, row, incline, pushUp));

			// press and push_up only hit chest_mid mainly, which is recovering; row only helps the day's regions.
			// incline still has chest_upper.
			assertThat(candidates).containsExactly(incline);
		}

		@Test
		void candidatesAreSortedById() {
			Candidate b = exercise("b", "push", List.of("chest_mid"), List.of());
			Candidate a = exercise("a", "push", List.of("chest_mid"), List.of());

			assertThat(Recommender.candidates(Set.of("chest_mid"), Set.of(), List.of(b, a))).containsExactly(a, b);
		}

	}

	@Nested
	class Picking {

		@Test
		void theBiggestScoreIsPickedFirstAndThreeSetsFillTheGaps() {
			// The test library's push day with default targets (chest 6, front delts 4) and an empty week:
			// incline 6 + 6 + 0.5 x 4 = 14, push-up 6 + 2 = 8, fly 6.
			// After incline: chest_upper 3, chest_mid 3, delt_front 2.5.
			// Then push-up 3 + 1.25 = 4.25 beats fly 3. After that chest_mid is full, so fly scores 0: stop.
			Candidate incline = exercise("incline_barbell_press", "horizontal_push",
				List.of("chest_upper", "chest_mid"), List.of("delt_front"));
			Candidate pushUp = exercise("push_up", "horizontal_push", List.of("chest_mid"), List.of("delt_front"));
			Candidate fly = exercise("cable_fly", "fly", List.of("chest_mid"), List.of());
			Map<String, Double> gaps = Map.of("chest_upper", 6.0, "chest_mid", 6.0, "delt_front", 4.0);

			assertThat(Recommender.pick(gaps, List.of(fly, incline, pushUp))).containsExactly(incline, pushUp);
		}

		@Test
		void aPatternUsedTwiceAlreadyScoresHalf() {
			Candidate a = exercise("a", "press", List.of("r1"), List.of());
			Candidate b = exercise("b", "press", List.of("r2"), List.of());
			Candidate c = exercise("c", "press", List.of("r3"), List.of());
			Candidate d = exercise("d", "row", List.of("r4"), List.of());
			Map<String, Double> gaps = Map.of("r1", 6.0, "r2", 6.0, "r3", 6.0, "r4", 6.0);

			// All score 6 at first. After a and b, c is a third "press": 6 x 0.5 = 3, so d (6) comes first.
			assertThat(Recommender.pick(gaps, List.of(a, b, c, d))).containsExactly(a, b, d, c);
			assertThat(Recommender.score(c, gaps, List.of(a, b))).isEqualTo(3.0);
			assertThat(Recommender.score(c, gaps, List.of(a))).isEqualTo(6.0);
		}

		@Test
		void repeatingAMainRegionScoresSeventyPercent() {
			Candidate first = exercise("x", "press", List.of("r1"), List.of("r2"));
			Candidate sameMain = exercise("y", "fly", List.of("r1"), List.of());
			Candidate otherMain = exercise("z", "raise", List.of("r2"), List.of());
			Map<String, Double> gaps = Map.of("r1", 12.0, "r2", 10.0);

			// x: 12 + 5 = 17. Then r1 = 9, r2 = 8.5: y would score 9, but its main region repeats x's,
			// so 9 x 0.7 = 6.3 and z (8.5) goes first.
			assertThat(Recommender.pick(gaps, List.of(first, sameMain, otherMain)))
				.containsExactly(first, otherMain, sameMain);
			assertThat(Recommender.score(sameMain, Map.of("r1", 9.0), List.of(first))).isEqualTo(6.3);
		}

		@Test
		void bothPenaltiesTogetherMultiply() {
			Candidate a = exercise("a", "press", List.of("r1"), List.of());
			Candidate b = exercise("b", "press", List.of("r2"), List.of());
			Candidate c = exercise("c", "press", List.of("r1"), List.of());

			// 10 x 0.5 x 0.7 = 3.5
			assertThat(Recommender.score(c, Map.of("r1", 10.0), List.of(a, b))).isEqualTo(3.5);
		}

		@Test
		void secondaryRegionsCountHalfAndOtherDaysRegionsNothing() {
			Candidate row = exercise("row", "pull", List.of("lats_lower"), List.of("lats_upper", "chest_mid"));

			// lats_lower 6 x 1 + lats_upper 4 x 0.5; chest_mid isn't in the gaps (another day).
			assertThat(Recommender.score(row, Map.of("lats_lower", 6.0, "lats_upper", 4.0), List.of())).isEqualTo(8.0);
		}

		@Test
		void aTieGoesToTheLowestId() {
			Candidate zebra = exercise("zebra_curl", "curl", List.of("biceps_long"), List.of());
			Candidate apple = exercise("apple_curl", "curl", List.of("biceps_short"), List.of());
			Map<String, Double> gaps = Map.of("biceps_long", 4.0, "biceps_short", 4.0);

			List<Candidate> sorted = Recommender.candidates(gaps.keySet(), Set.of(), List.of(zebra, apple));

			assertThat(Recommender.pick(gaps, sorted).getFirst()).isEqualTo(apple);
		}

		@Test
		void itStopsAtFiveExercises() {
			List<Candidate> seven = List.of(
				exercise("e1", "p1", List.of("r1"), List.of()), exercise("e2", "p2", List.of("r2"), List.of()),
				exercise("e3", "p3", List.of("r3"), List.of()), exercise("e4", "p4", List.of("r4"), List.of()),
				exercise("e5", "p5", List.of("r5"), List.of()), exercise("e6", "p6", List.of("r6"), List.of()),
				exercise("e7", "p7", List.of("r7"), List.of()));
			Map<String, Double> gaps = Map.of("r1", 6.0, "r2", 6.0, "r3", 6.0, "r4", 6.0, "r5", 6.0, "r6", 6.0,
				"r7", 6.0);

			assertThat(Recommender.pick(gaps, seven)).hasSize(5).extracting(Candidate::id)
				.containsExactly("e1", "e2", "e3", "e4", "e5");
		}

		@Test
		void itStopsWhenTheGapsAreFilled() {
			// One region with a gap of 6: two exercises (3 sets each) fill it, the third would score 0.
			Candidate a = exercise("a", "p1", List.of("r1"), List.of());
			Candidate b = exercise("b", "p2", List.of("r1"), List.of());
			Candidate c = exercise("c", "p3", List.of("r1"), List.of());

			assertThat(Recommender.pick(Map.of("r1", 6.0), List.of(a, b, c))).containsExactly(a, b);
		}

		@Test
		void aCoveredWeekGivesNoExercises() {
			Candidate a = exercise("a", "p1", List.of("r1"), List.of("r2"));

			assertThat(Recommender.pick(Map.of("r1", 0.0, "r2", 0.0), List.of(a))).isEmpty();
			assertThat(Recommender.pick(Map.of(), List.of(a))).isEmpty();
		}

		@Test
		void scoresAreRoundedSoFloatingPointNoiseCantBreakATie() {
			assertThat(Recommender.roundScore(0.1 + 0.2)).isEqualTo(0.3);
			assertThat(Recommender.roundScore(5 * 0.7)).isEqualTo(3.5);
		}

	}

	@Nested
	class Planned {

		@Test
		void primaryIsOneSecondaryIsHalfAndPrimaryWins() {
			Candidate incline = exercise("incline", "push", List.of("chest_upper", "chest_mid"), List.of("delt_front"));
			Candidate raise = exercise("raise", "raise", List.of("delt_front"), List.of("delt_side"));

			assertThat(Recommender.planned(List.of(incline, raise))).containsExactlyInAnyOrderEntriesOf(Map.of(
				"chest_mid", 1.0, "chest_upper", 1.0, "delt_front", 1.0, "delt_side", 0.5));
		}

		@Test
		void nothingPickedPlansNothing() {
			assertThat(Recommender.planned(List.of())).isEmpty();
		}

	}

	@Nested
	class WholeRecommendation {

		private final Candidate incline = exercise("incline_barbell_press", "horizontal_push",
			List.of("chest_upper", "chest_mid"), List.of("delt_front"));
		private final Candidate row = exercise("single_arm_cable_row", "horizontal_pull", List.of("lats_lower"),
			List.of("lats_upper", "biceps_long"));
		private final Map<String, Integer> targets = Map.of("chest_upper", 6, "chest_mid", 6, "delt_front", 4,
			"lats_upper", 6, "lats_lower", 6, "biceps_long", 4);

		@Test
		void afterPushComesPullWithItsExercisesAndPlan() {
			Recommendation recommendation = Recommender.recommend(PPL, PUSH.id(), targets,
				Map.of("chest_mid", 6.0), Set.of(), List.of(incline, row));

			assertThat(recommendation.day()).isEqualTo(PULL);
			assertThat(recommendation.exerciseIds()).containsExactly("single_arm_cable_row");
			assertThat(recommendation.planned()).containsExactlyInAnyOrderEntriesOf(
				Map.of("biceps_long", 0.5, "lats_lower", 1.0, "lats_upper", 0.5));
		}

		@Test
		void recoveringMainRegionsAreLeftOut() {
			Recommendation recommendation = Recommender.recommend(List.of(day(1, "Full body", "chest_upper",
				"chest_mid", "lats_lower", "lats_upper")), null, targets, Map.of(), Set.of("chest_upper", "chest_mid"),
				List.of(incline, row));

			assertThat(recommendation.exerciseIds()).containsExactly("single_arm_cable_row");
		}

		@Test
		void aDayWithoutUsableExercisesGivesAnEmptyPlan() {
			Recommendation recommendation = Recommender.recommend(PPL, PULL.id(), targets, Map.of(), Set.of(),
				List.of(incline, row));

			assertThat(recommendation.day()).isEqualTo(LEGS);
			assertThat(recommendation.exerciseIds()).isEmpty();
			assertThat(recommendation.planned()).isEmpty();
		}

	}

	// ---- helpers ----

	private static SplitDayOption day(int position, String name, String... regions) {
		return new SplitDayOption(UUID.randomUUID(), position, name, Set.of(regions));
	}

	private static Candidate exercise(String id, String pattern, List<String> primary, List<String> secondary) {
		return new Candidate(id, pattern, primary, secondary);
	}

}
