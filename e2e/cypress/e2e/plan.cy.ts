// The recommended next workout: NEXT UP on Home, starting it, and swapping an exercise.

import { PASSWORD } from "../support/testUser";

// The parts of the recommendation replies that these tests read.
type PlannedExercise = {
  exerciseId: string;
  name: string;
  primary: string[];
  secondary: string[];
};

type Recommendation = {
  splitDayId: string;
  name: string;
  exercises: PlannedExercise[];
};

type Alternative = PlannedExercise;

// The regions a list of exercises plans to hit: 1 for a main region, 0.5 for a helping one,
// written as text so two plans can be compared.
function planText(exercises: PlannedExercise[]): string {
  const plan: Record<string, number> = {};
  exercises.forEach((exercise) => {
    exercise.secondary.forEach((id) => (plan[id] = Math.max(plan[id] ?? 0, 0.5)));
    exercise.primary.forEach((id) => (plan[id] = 1));
  });
  return JSON.stringify(Object.entries(plan).sort());
}

describe("Plan", () => {
  beforeEach(() => {
    cy.uniqueEmail().then((email) => {
      cy.apiSignup(email, PASSWORD);
      cy.apiOnboard({ splitType: "PPL", units: "KG", equipment: ["barbell", "bench"] });
    });
    cy.request<Recommendation>("/api/recommendation/next").its("body").as("recommendation");
  });

  it("NEXT UP shows the recommended day, and START begins a workout with its exercises", () => {
    cy.visit("/");
    // A user without history starts at the first day of the split.
    cy.shown("next-up").should("be.visible");
    cy.shown("next-up-name").should("have.text", "Push");
    cy.shown("next-up-covered").should("not.exist");
    // An empty workout can still be started from the quiet button below.
    cy.shown("start-workout").should("be.visible");

    cy.shown("start-recommended").should("be.visible").and("be.enabled").click();
    cy.location("pathname").should("eq", "/workout");
    cy.shown("workout-name").should("have.text", "Push");
    cy.shown("workout-figure").should("be.visible");
    cy.get<Recommendation>("@recommendation").then((recommendation) => {
      expect(recommendation.exercises).to.have.length.greaterThan(0);
      recommendation.exercises.forEach((exercise, index) => {
        cy.shown(`exercise-row-${index + 1}`).should("contain.text", exercise.name);
      });
      cy.shown(`exercise-row-${recommendation.exercises.length + 1}`).should("not.exist");
    });

    // With a workout in progress, Home only offers RESUME.
    cy.visit("/");
    cy.shown("resume-workout").should("be.visible");
    cy.shown("next-up").should("not.exist");
    cy.shown("start-workout").should("not.exist");
  });

  it("swapping an exercise previews the new plan, then puts the alternative in the workout", () => {
    cy.get<Recommendation>("@recommendation").then((recommendation) => {
      const first = recommendation.exercises[0];
      cy.apiStartWorkout({
        splitDayId: recommendation.splitDayId,
        exercises: recommendation.exercises.map(({ exerciseId }) => ({ exerciseId })),
      });
      // The best-matching alternative for the first exercise that changes the plan. (Some only
      // hit regions the other exercises already cover, so the figure would rightly stay the same.)
      cy.request<Alternative[]>(
        `/api/recommendation/alternatives?exerciseId=${first.exerciseId}&splitDayId=${recommendation.splitDayId}`,
      )
        .its("body")
        .then((alternatives) => {
          const current = planText(recommendation.exercises);
          const changing = alternatives.find(
            (alternative) => planText([alternative, ...recommendation.exercises.slice(1)]) !== current,
          );
          expect(changing, "an alternative that changes the plan").to.not.equal(undefined);
          cy.wrap(changing).as("alternative");
        });
    });

    cy.visit("/workout");
    cy.shown("swap-1").should("be.visible").click();
    cy.location("pathname").should("eq", "/workout/1/swap");
    cy.shown("swap-figure").should("be.visible");
    cy.shown("use-alternative").should("be.disabled");

    cy.get<Alternative>("@alternative").then((alternative) => {
      // Tapping an alternative highlights it and redraws the planned figure, before anything is saved.
      cy.shown("swap-figure")
        .invoke("html")
        .then((before) => {
          cy.shown(`alt-option-${alternative.exerciseId}`)
            .should("have.attr", "aria-pressed", "false")
            .and("contain.text", alternative.name)
            .click();
          cy.shown(`alt-option-${alternative.exerciseId}`).should("have.attr", "aria-pressed", "true");
          cy.shown("swap-figure").invoke("html").should("not.eq", before);
        });

      cy.shown("use-alternative").should("be.enabled").click();
      cy.location("pathname").should("eq", "/workout");
      cy.shown("exercise-row-1").should("contain.text", alternative.name);
      cy.request<WorkoutSession>("/api/sessions/active").its("body.exercises.0.exerciseId").should("eq", alternative.exerciseId);
    });
    // The other exercises keep their places.
    cy.get<Recommendation>("@recommendation").then((recommendation) => {
      cy.shown("exercise-row-2").should("contain.text", recommendation.exercises[1].name);
    });
  });

  it("an exercise with a logged set can no longer be swapped", () => {
    cy.get<Recommendation>("@recommendation").then((recommendation) => {
      cy.apiStartWorkout({
        splitDayId: recommendation.splitDayId,
        exercises: recommendation.exercises.map(({ exerciseId }) => ({ exerciseId })),
      }).then((session) => {
        cy.apiAddSet(session.id, 1, { weightKg: 60, reps: 8, isWarmup: false });
      });
    });

    cy.visit("/workout");
    cy.shown("exercise-row-1").should("be.visible");
    cy.shown("swap-1").should("not.exist");
    cy.shown("swap-2").should("be.visible");
  });
});
