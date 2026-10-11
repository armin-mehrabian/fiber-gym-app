package app.fiber.workout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.fiber.catalog.ExerciseRepository;
import app.fiber.common.BadRequestException;
import app.fiber.common.ConflictException;
import app.fiber.settings.SplitDay;
import app.fiber.settings.SplitDayRepository;
import app.fiber.settings.UserSettingsRepository;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Plain unit test with mocked repositories, for the cases that are hard to reach over HTTP.
 * The full behaviour is covered end to end in WorkoutIntegrationTest.
 */
class WorkoutServiceTest {

	private static final UUID USER = UUID.randomUUID();

	private final WorkoutSessionRepository sessionRepository = mock(WorkoutSessionRepository.class);
	private final WorkoutSetRepository setRepository = mock(WorkoutSetRepository.class);
	private final ExerciseRepository exerciseRepository = mock(ExerciseRepository.class);
	private final SplitDayRepository splitDayRepository = mock(SplitDayRepository.class);
	private final UserSettingsRepository settingsRepository = mock(UserSettingsRepository.class);
	private final ExerciseHistoryService historyService = mock(ExerciseHistoryService.class);
	private final WorkoutService service = new WorkoutService(sessionRepository, setRepository, exerciseRepository,
		splitDayRepository, settingsRepository, historyService);

	// Two starts at the same moment both pass the "already active?" check; the database index stops
	// the second one, and the user must get the same 409 as usual, not a 500.
	@Test
	void aStartRejectedByTheDatabaseIsTheUsual409() {
		when(sessionRepository.existsByUserIdAndStatus(USER, SessionStatus.ACTIVE)).thenReturn(false);
		when(sessionRepository.saveAndFlush(any())).thenThrow(violationOf("workout_session_one_active_per_user"));

		assertThatThrownBy(() -> service.start(USER, null, List.of()))
			.isInstanceOf(ConflictException.class)
			.hasMessage("A workout is already in progress");
	}

	// Any other database error must not be disguised as "already in progress".
	@Test
	void otherDatabaseErrorsOnStartAreNotTurnedInto409() {
		DataIntegrityViolationException otherError = violationOf("workout_session_user_id_fkey");
		when(sessionRepository.existsByUserIdAndStatus(USER, SessionStatus.ACTIVE)).thenReturn(false);
		when(sessionRepository.saveAndFlush(any())).thenThrow(otherError);

		assertThatThrownBy(() -> service.start(USER, null, List.of())).isSameAs(otherError);
	}

	@Test
	void anErrorWithoutAConstraintNameIsNotTheActiveSessionIndex() {
		assertThat(WorkoutService.isSecondActiveSession(new DataIntegrityViolationException("no cause"))).isFalse();
	}

	@Test
	void aStartWhileOneIsActiveIs409WithoutSaving() {
		when(sessionRepository.existsByUserIdAndStatus(USER, SessionStatus.ACTIVE)).thenReturn(true);

		assertThatThrownBy(() -> service.start(USER, null, List.of())).isInstanceOf(ConflictException.class);
		verify(sessionRepository, never()).saveAndFlush(any());
	}

	@Test
	void anotherUsersSplitDayIsRejected() {
		SplitDay othersDay = new SplitDay(UUID.randomUUID(), 1, "Push", List.of("chest_mid"));
		UUID dayId = UUID.randomUUID();
		when(splitDayRepository.findById(dayId)).thenReturn(Optional.of(othersDay));

		assertThatThrownBy(() -> service.start(USER, dayId, List.of()))
			.isInstanceOf(BadRequestException.class)
			.satisfies(e -> assertThat(((BadRequestException) e).getFields())
				.containsEntry("splitDayId", "unknown split day"));
	}

	@Test
	void historyWeeksMustBeBetween1And52() {
		for (int weeks : new int[] { 0, 53 }) {
			assertThatThrownBy(() -> service.history(USER, weeks))
				.isInstanceOf(BadRequestException.class)
				.satisfies(e -> assertThat(((BadRequestException) e).getFields())
					.containsEntry("weeks", "must be between 1 and 52"));
		}
	}

	@Test
	void finishingAFinishedWorkoutIs409() {
		UUID sessionId = UUID.randomUUID();
		WorkoutSession session = new WorkoutSession(USER, null, "Workout", Instant.parse("2026-10-05T10:00:00Z"));
		session.finish(Instant.parse("2026-10-05T11:00:00Z"));
		when(sessionRepository.findByIdAndUserIdForUpdate(sessionId, USER)).thenReturn(Optional.of(session));

		assertThatThrownBy(() -> service.finish(USER, sessionId))
			.isInstanceOf(ConflictException.class)
			.hasMessage("This workout is finished");
		assertThat(session.getFinishedAt()).isEqualTo(Instant.parse("2026-10-05T11:00:00Z")); // unchanged
	}

	/** The exception Spring throws when Hibernate reports a broken database constraint with this name. */
	private static DataIntegrityViolationException violationOf(String constraintName) {
		SQLException sqlError = new SQLException("duplicate key value violates unique constraint", "23505");
		return new DataIntegrityViolationException("could not execute statement",
			new ConstraintViolationException("could not execute statement", sqlError, constraintName));
	}

}
