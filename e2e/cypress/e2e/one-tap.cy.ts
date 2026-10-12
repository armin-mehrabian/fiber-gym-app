// One-tap logging on the workout screen: ✓ DONE logs an exercise's plan in one go, and CHANGE
// logs different numbers (weight, reps, sets) when the day went differently.

import { PASSWORD } from "../support/testUser";

// Bench press (6–10 reps) and barbell curl (8–12 reps) both need only a barbell and a bench.
const BENCH = "barbell_bench_press";
const CURL = "barbell_curl";

type Exercise = { equipment: string[] };

// A fresh, onboarded user with the given units and only a barbell + bench.
function newUser(units: Units) {
  cy.uniqueEmail().then((email) => {
    cy.apiSignup(email, PASSWORD);
    cy.apiOnboard({ splitType: "PPL", units, equipment: ["barbell", "bench"] });
  });
}

// Every set of an exercise must be a finished working set of exactly weightKg × reps.
function expectSets(sets: WorkoutSet[], count: number, weightKg: number, reps: number) {
  expect(sets, "logged sets").to.have.length(count);
  sets.forEach((set) => {
    expect(set.completed, "set is ticked off").to.equal(true);
    expect(set.isWarmup, "set is a working set").to.equal(false);
    expect(Number(set.weightKg), "weight in kg").to.be.closeTo(weightKg, 0.001);
    expect(set.reps, "reps").to.equal(reps);
  });
}

describe("One-tap logging", () => {
  beforeEach(() => {
    cy.intercept("POST", "/api/sessions/*/exercises/*/complete").as("complete");
  });

  it("one tap on DONE logs the planned sets of the recommended workout, and a second tap adds nothing", () => {
    newUser("KG");

    cy.visit("/");
    cy.shown("start-recommended").should("be.visible").and("be.enabled").click();
    cy.location("pathname").should("eq", "/workout");

    cy.request<WorkoutSession>("/api/sessions/active")
      .its("body.exercises.0")
      .then((first: SessionExercise) => {
        cy.request<Exercise>(`/api/exercises/${first.exerciseId}`)
          .its("body")
          .then((exercise) => {
            // The plan: the suggestion when there is one, otherwise bodyweight (0) or an empty bar
            // (20 kg), with reps at the top of the range, for the target number of sets.
            const bodyweight = exercise.equipment.length === 0;
            const weightKg = first.suggestedWeightKg ?? (bodyweight ? 0 : 20);
            const reps = first.suggestedReps ?? first.repRange[1];
            const load = weightKg === 0 ? "Bodyweight" : `${weightKg} kg`;
            cy.shown("exercise-row-1")
              .should("have.attr", "data-done", "false")
              .and("contain.text", `${first.targetSets} × ${reps} · ${load}`);

            // A second tap while the first is still saving must not log the sets again. The reply is
            // held back briefly, so the second tap surely lands while the first request is in flight.
            cy.intercept("POST", "/api/sessions/*/exercises/*/complete", (request) => {
              request.on("response", (response) => {
                response.setDelay(1000);
              });
            });
            cy.shown("exercise-done-1").should("be.visible").and("be.enabled").click();
            cy.shown("exercise-done-1").should("be.disabled");
            cy.shown("exercise-change-1").should("be.disabled");
            // The forced tap on the disabled button does nothing; it only mimics an impatient user.
            // The proof is the disabled button, exactly 1 complete request and the server's set count.
            cy.shown("exercise-done-1").click({ force: true });
            cy.wait("@complete").its("request.body").should("deep.equal", { weightKg, reps, sets: first.targetSets });
            cy.shown("exercise-row-1").should("have.attr", "data-done", "true");
            cy.shown("exercise-done-1").should("not.exist");
            cy.shown("exercise-change-1").should("not.exist");
            cy.get("@complete.all").should("have.length", 1);

            cy.request<WorkoutSession>("/api/sessions/active")
              .its("body.exercises.0.sets")
              .then((sets: WorkoutSet[]) => expectSets(sets, first.targetSets, weightKg, reps));
            // The next exercise is untouched.
            cy.shown("exercise-row-2").should("have.attr", "data-done", "false");
            cy.shown("exercise-done-2").should("be.visible");

            // Tapping the name opens the per-set screen, where every logged set is ticked off.
            cy.shown("exercise-row-1").click();
            cy.location("pathname").should("eq", "/workout/1");
            for (let setNumber = 1; setNumber <= first.targetSets; setNumber++) {
              cy.shown(`set-done-${setNumber}`).should("have.attr", "aria-pressed", "true");
              cy.shown(`set-reps-${setNumber}`).should("have.text", String(reps));
            }
            cy.shown(`set-row-${first.targetSets + 1}`).should("not.exist");
          });
      });
  });

  it("CHANGE logs the stepped weight, reps and sets; CANCEL logs nothing", () => {
    newUser("KG");
    cy.apiStartWorkout({ exercises: [{ exerciseId: BENCH }, { exerciseId: CURL }] }).as("workout");

    cy.visit("/workout");
    // A first attempt: an empty bar and the top of the range (bench press: 10), 3 sets.
    cy.shown("exercise-row-1").should("contain.text", "3 × 10 · 20 kg");
    cy.shown("exercise-change-1").should("be.visible").and("be.enabled").click();
    cy.shown("change-weight").should("have.text", "20");
    cy.shown("change-reps").should("have.text", "10");
    cy.shown("change-sets").should("have.text", "3");

    cy.shown("change-weight-inc").click();
    cy.shown("change-weight-inc").click();
    cy.shown("change-weight").should("have.text", "25");
    cy.shown("change-reps-dec").click();
    cy.shown("change-reps").should("have.text", "9");
    cy.shown("change-sets-inc").click();
    cy.shown("change-sets").should("have.text", "4");

    cy.shown("change-save").should("be.enabled").click();
    cy.wait("@complete").its("request.body").should("deep.equal", { weightKg: 25, reps: 9, sets: 4 });
    cy.shown("exercise-row-1").should("have.attr", "data-done", "true");
    cy.shown("change-save").should("not.exist");
    cy.get<WorkoutSession>("@workout").then((workout) => {
      cy.request<WorkoutSession>(`/api/sessions/${workout.id}`)
        .its("body.exercises.0.sets")
        .then((sets: WorkoutSet[]) => expectSets(sets, 4, 25, 9));
    });

    // CANCEL closes the panel without logging, and the plan is back the next time it opens.
    cy.shown("exercise-change-2").should("be.visible").click();
    cy.shown("change-reps").should("have.text", "12");
    cy.shown("change-reps-inc").click();
    cy.shown("change-reps").should("have.text", "13");
    cy.shown("change-cancel").click();
    cy.shown("change-save").should("not.exist");
    cy.shown("exercise-done-2").should("be.visible");
    cy.shown("exercise-row-2").should("have.attr", "data-done", "false");
    cy.shown("exercise-change-2").click();
    cy.shown("change-reps").should("have.text", "12");
    cy.shown("change-cancel").click();
    cy.shown("change-save").should("not.exist");

    cy.get("@complete.all").should("have.length", 1);
    cy.get<WorkoutSession>("@workout").then((workout) => {
      cy.request<WorkoutSession>(`/api/sessions/${workout.id}`).its("body.exercises.1.sets").should("have.length", 0);
    });
  });

  it("a pound user changes the weight in pounds, and kg are saved", () => {
    newUser("LB");
    cy.apiStartWorkout({ exercises: [{ exerciseId: BENCH }] }).as("workout");

    cy.visit("/workout");
    // The empty bar is 45 lb; one tap adds 5 lb.
    cy.shown("exercise-row-1").should("contain.text", "3 × 10 · 45 lb");
    cy.shown("exercise-change-1").should("be.visible").and("be.enabled").click();
    cy.shown("change-weight").should("have.text", "45");
    cy.shown("change-weight-inc").click();
    cy.shown("change-weight").should("have.text", "50");
    cy.shown("change-save").click();
    cy.wait("@complete");
    cy.shown("exercise-row-1").should("have.attr", "data-done", "true");

    // 50 lb ÷ 2.20462 = 22.68 kg.
    cy.get<WorkoutSession>("@workout").then((workout) => {
      cy.request<WorkoutSession>(`/api/sessions/${workout.id}`)
        .its("body.exercises.0.sets")
        .then((sets: WorkoutSet[]) => expectSets(sets, 3, 22.68, 10));
    });
  });
});
