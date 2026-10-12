// Progress between workouts: weight suggestions, the summary after FINISH (with new bests)
// and the exercise page.

import { PASSWORD } from "../support/testUser";

// Barbell curl: 8–12 reps, an upper-body lift (+2.5 kg steps).
const CURL = "barbell_curl";
// Romanian deadlift: 8–12 reps, a lower-body lift (+5 kg steps).
const RDL = "romanian_deadlift";

type Exercise = { name: string; cue: string };

// A finished workout of one exercise where every working set hit 12 reps; yields the workout.
function apiFinishedAllTwelves(exerciseId: string, weightKg: number): Cypress.Chainable<WorkoutSession> {
  return cy.apiStartWorkout().then((session) => {
    cy.apiAddExercise(session.id, exerciseId);
    for (let set = 0; set < 3; set++) {
      cy.apiAddSet(session.id, 1, { weightKg, reps: 12, isWarmup: false, completed: true });
    }
    return cy.apiFinish(session.id);
  });
}

// Starts a new workout with just this exercise; yields the workout.
function apiStartWith(exerciseId: string): Cypress.Chainable<WorkoutSession> {
  return cy.apiStartWorkout({ exercises: [{ exerciseId }] });
}

describe("Progress", () => {
  beforeEach(() => {
    cy.uniqueEmail().then((email) => {
      cy.apiSignup(email, PASSWORD);
      cy.apiOnboard({ splitType: "PPL", units: "KG", equipment: ["barbell", "bench"] });
    });
  });

  it("after every set hit the top of the range, the next workout suggests 2.5 kg more and starts there", () => {
    apiFinishedAllTwelves(CURL, 30);
    apiStartWith(CURL);

    cy.visit("/workout/1");
    cy.shown("last-time").should("have.text", "Last: 30 × 12 · 12 · 12");
    cy.shown("suggestion").should("have.text", "+2.5 kg · all sets hit 12");
    // The first new set starts at the suggested weight and the bottom of the range.
    cy.shown("add-set").should("be.visible").and("be.enabled").click();
    cy.shown("set-weight-1").should("have.text", "32.5");
    cy.shown("set-reps-1").should("have.text", "8");
  });

  it("a lower-body lift that hit the top of the range goes up 5 kg", () => {
    apiFinishedAllTwelves(RDL, 60);
    apiStartWith(RDL);

    cy.visit("/workout/1");
    cy.shown("last-time").should("have.text", "Last: 60 × 12 · 12 · 12");
    cy.shown("suggestion").should("have.text", "+5 kg · all sets hit 12");
    cy.shown("add-set").should("be.visible").and("be.enabled").click();
    cy.shown("set-weight-1").should("have.text", "65");
    cy.shown("set-reps-1").should("have.text", "8");
  });

  it("a first attempt has no suggestion from the past and is not a new best", () => {
    apiStartWith(CURL).then((session) => {
      cy.wrap(session.id).as("sessionId");
    });

    cy.visit("/workout/1");
    cy.shown("suggestion").should("have.text", "First time · pick a weight");
    cy.shown("last-time").should("not.exist");
    cy.shown("add-set").should("be.visible").and("be.enabled").click();
    cy.shown("set-done-1").click();
    cy.shown("set-done-1").should("have.attr", "aria-pressed", "true");
    cy.shown("log-done").click();
    cy.location("pathname").should("eq", "/workout");

    cy.shown("finish-workout").should("be.visible").and("be.enabled").click();
    cy.get<string>("@sessionId").then((sessionId) => {
      cy.location("pathname").should("eq", `/summary/${sessionId}`);
    });
    cy.shown("summary-title").should("have.text", "Workout · Done");
    cy.get('[data-testid^="summary-pr-"]').should("not.exist");
    // One set finishes no region for the week, which still counts as a good day.
    cy.shown("summary-lit").should("have.text", "Kept the lights on");
  });

  it("beating an earlier best shows a new best on the summary, and DONE goes home", () => {
    apiFinishedAllTwelves(CURL, 30);
    apiStartWith(CURL).then((session) => {
      cy.wrap(session.id).as("sessionId");
    });

    // 32.5 × 10 beats last time's 30 × 12 (estimated one-rep max 43.3 vs 42).
    cy.visit("/workout/1");
    cy.shown("add-set").should("be.visible").and("be.enabled").click();
    cy.shown("set-weight-1").should("have.text", "32.5");
    cy.shown("set-reps-inc-1").click();
    cy.shown("set-reps-inc-1").click();
    cy.shown("set-reps-1").should("have.text", "10");
    cy.shown("set-done-1").click();
    cy.shown("set-done-1").should("have.attr", "aria-pressed", "true");
    cy.shown("log-done").click();
    cy.location("pathname").should("eq", "/workout");

    cy.shown("finish-workout").should("be.visible").and("be.enabled").click();
    cy.get<string>("@sessionId").then((sessionId) => {
      cy.location("pathname").should("eq", `/summary/${sessionId}`);
      cy.shown("summary-title").should("have.text", "Workout · Done");
      cy.shown("summary-figure").should("be.visible");
    });
    // Last workout's 3 sets plus this one reach the biceps' weekly target (4), so both heads light up.
    // The summary names them in words; the old week percentage line is gone.
    cy.shown("summary-lit").should("have.text", "Lit up: Biceps, long head · Biceps, short head");
    cy.shown("summary-week").should("not.exist");
    cy.shown(`summary-pr-${CURL}`).should("contain.text", "New best · Barbell curl · 32.5 × 10");

    cy.shown("summary-done").click();
    cy.location("pathname").should("eq", "/");
    cy.shown("next-up").should("be.visible");
  });

  it("the exercise page shows the cue, best, last workout and history", () => {
    apiFinishedAllTwelves(CURL, 30).as("previous");
    apiStartWith(CURL);
    cy.request<Exercise>(`/api/exercises/${CURL}`).its("body").as("exercise");

    cy.visit("/workout/1");
    cy.shown("exercise-info").should("be.visible").click();
    cy.location("pathname").should("eq", `/exercise/${CURL}`);
    cy.shown("exercise-figure").should("be.visible");
    cy.shown("exercise-name").should("have.text", "Barbell curl");
    cy.get<Exercise>("@exercise").then((exercise) => {
      cy.shown("exercise-cue").should("have.text", exercise.cue);
    });
    cy.shown("exercise-best").should("have.text", "30 × 12");
    cy.shown("exercise-last").should("have.text", "30 × 12");
    cy.get<WorkoutSession>("@previous").then((previous) => {
      cy.shown(`exercise-history-${previous.id}`).should("contain.text", "30 × 12 · 12 · 12");
    });
    // Only the finished workout is history; the one in progress isn't listed.
    cy.get('[data-testid^="exercise-history-"]:visible').should("have.length", 1);
  });
});
