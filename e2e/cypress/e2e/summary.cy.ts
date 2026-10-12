// The summary after FINISH: which regions the workout lit up for the week, and any badge earned.

import { PASSWORD } from "../support/testUser";

// Incline, flat and decline press: together they cover all three chest regions.
const CHEST_DAY = ["incline_barbell_press", "barbell_bench_press", "decline_barbell_press"];

// Starts a workout with these exercises through the API and saves it as @workout.
function apiStartWith(exerciseIds: string[]) {
  return cy.apiStartWorkout({ exercises: exerciseIds.map((exerciseId) => ({ exerciseId })) }).as("workout");
}

// Taps FINISH and waits for this workout's summary.
function finishOnScreen() {
  cy.shown("finish-workout").should("be.visible").and("be.enabled").click();
  cy.get<WorkoutSession>("@workout").then((workout) => {
    cy.location("pathname").should("eq", `/summary/${workout.id}`);
  });
}

describe("Summary", () => {
  beforeEach(() => {
    cy.uniqueEmail().then((email) => {
      cy.apiSignup(email, PASSWORD);
      cy.apiOnboard({ splitType: "PPL", units: "KG", equipment: ["barbell", "bench"] });
    });
  });

  it("finishing a workout that completes the chest names the lit regions and celebrates the chest badge", () => {
    // Six sets of incline and flat press are logged already; the decline press is done on screen.
    apiStartWith(CHEST_DAY).then((workout) => {
      cy.apiCompleteExercise(workout.id, 1, { weightKg: 60, reps: 8, sets: 6 });
      cy.apiCompleteExercise(workout.id, 2, { weightKg: 60, reps: 8, sets: 6 });
    });

    cy.visit("/workout");
    cy.shown("exercise-change-3").should("be.visible").and("be.enabled").click();
    cy.shown("change-sets").should("have.text", "3");
    Cypress._.times(3, () => cy.shown("change-sets-inc").click());
    cy.shown("change-sets").should("have.text", "6");
    cy.shown("change-save").click();
    cy.shown("exercise-row-3").should("have.attr", "data-done", "true");

    finishOnScreen();
    cy.shown("summary-title").should("have.text", "Workout · Done");
    cy.shown("summary-figure").should("be.visible");
    // Every region that reached its weekly target in this workout, in region order. Besides the
    // chest, the presses' helping sets also finish the front delts and the lateral triceps (target 4).
    cy.shown("summary-lit").should(
      "have.text",
      "Lit up: Upper chest · Mid chest · Lower chest · Front delts · Triceps, lateral + medial heads",
    );
    cy.shown("summary-badge-chest").should("be.visible");
    cy.get('[data-testid^="summary-badge-"]:visible').should("have.length", 1);
    // The old "Week 41% → 58%" line is gone.
    cy.shown("summary-week").should("not.exist");

    // DONE goes home, where the chest badge is now filled.
    cy.shown("summary-done").click();
    cy.location("pathname").should("eq", "/");
    cy.shown("badge-chest").should("have.attr", "data-done", "true");
  });

  it("a badge already earned this week isn't celebrated again", () => {
    // An earlier workout this week already finished every chest region.
    apiStartWith(CHEST_DAY).then((workout) => {
      CHEST_DAY.forEach((_, index) => cy.apiCompleteExercise(workout.id, index + 1, { weightKg: 60, reps: 8, sets: 6 }));
      cy.apiFinish(workout.id);
    });
    apiStartWith(["incline_barbell_press"]);

    cy.visit("/workout");
    cy.shown("exercise-done-1").should("be.visible").and("be.enabled").click();
    cy.shown("exercise-row-1").should("have.attr", "data-done", "true");

    finishOnScreen();
    cy.shown("summary-lit").should("have.text", "Kept the lights on");
    cy.get('[data-testid^="summary-badge-"]').should("not.exist");
    cy.shown("summary-done").should("be.visible");
  });
});
