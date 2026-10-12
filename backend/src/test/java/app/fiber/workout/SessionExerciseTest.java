package app.fiber.workout;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Positions and set numbers inside one workout, without a database. */
class SessionExerciseTest {

	private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

	@Test
	void exercisesAreAppendedFromPosition1() {
		WorkoutSession session = new WorkoutSession(UUID.randomUUID(), null, "Workout", NOW);

		session.addExercise("push_up", 3, 8, 20);
		session.addExercise("cable_fly", 3, 10, 15);

		assertThat(session.getExercises()).extracting(SessionExercise::getPosition).containsExactly(1, 2);
		assertThat(session.exerciseAt(2)).get().extracting(SessionExercise::getExerciseId).isEqualTo("cable_fly");
		assertThat(session.exerciseAt(3)).isEmpty();
	}

	@Test
	void newSetsGetTheNextNumberAndStartNotCompleted() {
		SessionExercise exercise = newExercise();

		WorkoutSet first = exercise.addSet(new BigDecimal("20"), 10, true, NOW);
		WorkoutSet second = exercise.addSet(new BigDecimal("22.5"), 8, false, NOW);

		assertThat(first.getSetNumber()).isEqualTo(1);
		assertThat(second.getSetNumber()).isEqualTo(2);
		assertThat(second.isCompleted()).isFalse();
		assertThat(first.isWarmup()).isTrue();
		assertThat(second.getWeightKg()).isEqualByComparingTo("22.5").hasToString("22.50");
	}

	@Test
	void removingASetNumbersTheRestFrom1Again() {
		SessionExercise exercise = newExercise();
		WorkoutSet first = exercise.addSet(BigDecimal.TEN, 10, false, NOW);
		WorkoutSet second = exercise.addSet(BigDecimal.TEN, 9, false, NOW);
		WorkoutSet third = exercise.addSet(BigDecimal.TEN, 8, false, NOW);

		exercise.removeSet(second);

		assertThat(exercise.getSets()).containsExactly(first, third);
		assertThat(exercise.getSets()).extracting(WorkoutSet::getSetNumber).containsExactly(1, 2);
		assertThat(exercise.addSet(BigDecimal.TEN, 7, false, NOW).getSetNumber()).isEqualTo(3);
	}

	@Test
	void removingTheOnlySetLeavesNone() {
		SessionExercise exercise = newExercise();
		WorkoutSet only = exercise.addSet(BigDecimal.ZERO, 12, false, NOW);

		exercise.removeSet(only);

		assertThat(exercise.getSets()).isEmpty();
	}

	@Test
	void oneTapAddsCompletedWorkingSetsAfterTheExistingOnes() {
		SessionExercise exercise = newExercise();
		WorkoutSet warmup = exercise.addSet(new BigDecimal("20"), 10, true, NOW);

		exercise.addCompletedSets(new BigDecimal("60"), 10, 3, NOW);

		assertThat(exercise.getSets()).first().isSameAs(warmup);
		assertThat(exercise.getSets()).extracting(WorkoutSet::getSetNumber).containsExactly(1, 2, 3, 4);
		assertThat(exercise.getSets().subList(1, 4)).allSatisfy(set -> {
			assertThat(set.isCompleted()).isTrue();
			assertThat(set.isWarmup()).isFalse();
			assertThat(set.getWeightKg()).hasToString("60.00");
			assertThat(set.getReps()).isEqualTo(10);
		});
	}

	private static SessionExercise newExercise() {
		WorkoutSession session = new WorkoutSession(UUID.randomUUID(), null, "Workout", NOW);
		return session.addExercise("cable_fly", 3, 10, 15);
	}

}
