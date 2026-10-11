// Finished workouts listed by week, and the detail of one workout.

import { PASSWORD, TIMEZONE } from "../support/testUser";

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

// The Monday (YYYY-MM-DD) of the week a moment falls in, on the user's own calendar.
// A workout belongs to the week it started in, in the user's timezone.
function mondayOf(isoTimestamp: string): string {
  // "en-CA" writes dates as YYYY-MM-DD.
  const localDate = new Intl.DateTimeFormat("en-CA", { timeZone: TIMEZONE }).format(new Date(isoTimestamp));
  const day = new Date(`${localDate}T00:00:00Z`);
  const daysSinceMonday = (day.getUTCDay() + 6) % 7; // getUTCDay: Sunday = 0
  day.setUTCDate(day.getUTCDate() - daysSinceMonday);
  return day.toISOString().slice(0, 10);
}

// "2026-10-05" → "Week of 5 Oct", the header text before the screen uppercases it.
function weekHeader(weekStart: string): string {
  const [, month, day] = weekStart.split("-").map(Number);
  return `Week of ${day} ${MONTHS[month - 1]}`;
}

describe("History", () => {
  beforeEach(() => {
    cy.uniqueEmail().then((email) => {
      cy.apiSignup(email, PASSWORD);
      cy.apiOnboard({ splitType: "PPL", units: "KG", equipment: ["barbell", "bench"] });
    });
  });

  it("finished workouts appear under their week with only the working sets counted", () => {
    // Workout A: a ticked warm-up, 2 ticked working sets and 1 never ticked → 2 working sets.
    cy.apiStartWorkout().then((session) => {
      cy.wrap(session).as("first");
      cy.apiAddExercise(session.id, "barbell_bench_press");
      cy.apiAddSet(session.id, 1, { weightKg: 20, reps: 10, isWarmup: true, completed: true });
      cy.apiAddSet(session.id, 1, { weightKg: 60, reps: 8, isWarmup: false, completed: true });
      cy.apiAddSet(session.id, 1, { weightKg: 62.5, reps: 7, isWarmup: false, completed: true });
      cy.apiAddSet(session.id, 1, { weightKg: 62.5, reps: 6, isWarmup: false });
      cy.apiFinish(session.id);
    });
    // Workout B: 1 ticked working set.
    cy.apiStartWorkout().then((session) => {
      cy.wrap(session).as("second");
      cy.apiAddExercise(session.id, "barbell_curl");
      cy.apiAddSet(session.id, 1, { weightKg: 30, reps: 10, isWarmup: false, completed: true });
      cy.apiFinish(session.id);
    });
    // Workout C is still in progress, so it isn't history yet.
    cy.apiStartWorkout().as("inProgress");

    cy.visit("/history");
    cy.get<WorkoutSession>("@first").then((first) => {
      const weekStart = mondayOf(first.startedAt);
      cy.shown(`history-week-${weekStart}`)
        .should("be.visible")
        .and("contain.text", weekHeader(weekStart))
        .within(() => {
          cy.shown(`history-session-${first.id}`).should("contain.text", "Workout").and("contain.text", "2 sets");
          cy.get<WorkoutSession>("@second").then((second) => {
            cy.shown(`history-session-${second.id}`).should("contain.text", "1 set");
            // Newest workout first.
            cy.get('[data-testid^="history-session-"]:visible')
              .first()
              .should("have.attr", "data-testid", `history-session-${second.id}`);
          });
        });
    });
    cy.get<WorkoutSession>("@inProgress").then((inProgress) => {
      cy.shown(`history-session-${inProgress.id}`).should("not.exist");
    });

    // Tapping a workout opens every set of it.
    cy.get<WorkoutSession>("@first").then((first) => {
      cy.shown(`history-session-${first.id}`).click();
      cy.location("pathname").should("eq", `/history/${first.id}`);
    });
    cy.shown("session-detail-exercise-1").should("contain.text", "Barbell bench press");
    cy.shown("session-detail-set-1-1").should("have.text", "W 20 × 10");
    cy.shown("session-detail-set-1-2").should("have.text", "60 × 8");
    cy.shown("session-detail-set-1-3").should("have.text", "62.5 × 7");
    cy.shown("session-detail-set-1-4").should("have.text", "62.5 × 6");
    cy.shown("session-detail-exercise-2").should("not.exist");
  });

  it("a user with no finished workouts sees the empty history", () => {
    cy.visit("/history");
    cy.shown("nav-history").should("have.attr", "aria-current", "page");
    cy.get('[data-testid^="history-week-"]').should("not.exist");
    cy.get('[data-testid^="history-session-"]').should("not.exist");
  });
});
