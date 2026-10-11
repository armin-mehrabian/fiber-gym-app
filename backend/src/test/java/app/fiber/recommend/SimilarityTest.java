package app.fiber.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SimilarityTest {

	private static final Candidate INCLINE = new Candidate("incline_barbell_press", "horizontal_push",
		List.of("chest_upper", "chest_mid"), List.of("delt_front"));
	private static final Candidate PUSH_UP = new Candidate("push_up", "horizontal_push", List.of("chest_mid"),
		List.of("delt_front"));
	private static final Candidate FLY = new Candidate("cable_fly", "fly", List.of("chest_mid"), List.of());
	private static final Candidate ROW = new Candidate("single_arm_cable_row", "horizontal_pull",
		List.of("lats_lower"), List.of("lats_upper", "biceps_long"));

	@Test
	void theSameRegionsWithTheSameRolesAreOne() {
		Candidate copy = new Candidate("other_incline", "horizontal_push", List.of("chest_mid", "chest_upper"),
			List.of("delt_front"));

		assertThat(Similarity.similarity(INCLINE, copy)).isEqualTo(1.0);
	}

	@Test
	void noRegionInCommonIsZero() {
		assertThat(Similarity.similarity(INCLINE, ROW)).isZero();
	}

	@Test
	void itIsTheSumOfMinimumsOverTheSumOfMaximums() {
		// min: chest_mid 1 + delt_front 0.5 = 1.5; max: chest_upper 1 + chest_mid 1 + delt_front 0.5 = 2.5
		assertThat(Similarity.similarity(INCLINE, PUSH_UP)).isEqualTo(0.6);
		assertThat(Similarity.similarity(PUSH_UP, INCLINE)).isEqualTo(0.6);
		// min: chest_mid 1; max: 1 + 1 + 0.5
		assertThat(Similarity.similarity(INCLINE, FLY)).isEqualTo(0.4);
	}

	@Test
	void aPrimaryAgainstASecondaryCountsHalf() {
		Candidate helps = new Candidate("helps", "x", List.of(), List.of("chest_mid"));

		// min 0.5 / max 1
		assertThat(Similarity.similarity(FLY, helps)).isEqualTo(0.5);
	}

	@Test
	void exercisesWithoutRegionsAreNotSimilar() {
		Candidate empty = new Candidate("empty", "x", List.of(), List.of());

		assertThat(Similarity.similarity(empty, empty)).isZero();
	}

	@Test
	void alternativesLeaveOutTheExerciseItselfAndUnrelatedOnesMostSimilarFirst() {
		List<Alternative> alternatives = Similarity.alternatives(INCLINE, List.of(FLY, ROW, INCLINE, PUSH_UP));

		assertThat(alternatives).containsExactly(new Alternative("push_up", 0.6), new Alternative("cable_fly", 0.4));
	}

	@Test
	void tiesAreSortedByIdAndOnlyTheTopFiveAreKept() {
		List<Candidate> sameAsFly = List.of("f", "b", "e", "a", "d", "c").stream()
			.map(id -> new Candidate(id, "fly", List.of("chest_mid"), List.of()))
			.toList();

		assertThat(Similarity.alternatives(FLY, sameAsFly)).extracting(Alternative::exerciseId)
			.containsExactly("a", "b", "c", "d", "e");
	}

	@Test
	void similarityIsRoundedToTwoDecimals() {
		// min 1 / max 1.5 = 0.666...
		Candidate flyPlusHelp = new Candidate("fly_plus", "fly", List.of("chest_mid"), List.of("delt_front"));

		assertThat(Similarity.alternatives(FLY, List.of(flyPlusHelp))).containsExactly(
			new Alternative("fly_plus", 0.67));
	}

	@Test
	void noRelatedExercisesGivesAnEmptyList() {
		assertThat(Similarity.alternatives(ROW, List.of(INCLINE, PUSH_UP, FLY))).isEmpty();
	}

}
