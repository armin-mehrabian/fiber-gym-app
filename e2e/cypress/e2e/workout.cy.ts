// Logging a workout: start, add exercises, log and remove sets, finish, resume, discard.

import { PASSWORD } from "../support/testUser";

// Bench press (6–10 reps) and barbell curl (8–12 reps) both need only a barbell and a bench.
const BENCH = "barbell_bench_press";
const CURL = "barbell_curl";

// A fresh, onboarded user with the given units and only a barbell + bench (all these tests need).
function newUser(units: Units) {
  cy.uniqueEmail().then((email) => {
    cy.apiSignup(email, PASSWORD);
    cy.apiOnboard({ splitType: "PPL", units, equipment: ["barbell", "bench"] });
  });
}

// Picks an exercise on the add screen by searching for it first.
function addExerciseBySearch(search: string, exerciseId: string, otherExerciseId: string) {
  cy.shown("add-exercise").should("be.visible").click();
  cy.location("pathname").should("eq", "/workout/add");
  cy.shown("exercise-search").should("be.visible").and("be.enabled").type(search);
  // The search lands in the URL once typing pauses, and the list shrinks to the matches.
  cy.location("search").should("eq", `?q=${encodeURIComponent(search)}`);
  cy.shown(`exercise-option-${otherExerciseId}`).should("not.exist");
  cy.shown(`exercise-option-${exerciseId}`).should("be.visible").and("be.enabled").click();
  cy.location("pathname").should("eq", "/workout");
}

describe("Workout", () => {
  it("a user starts a workout, adds exercises, logs sets with a warm-up, finishes and sees the summary", () => {
    newUser("KG");

    // Start from home.
    cy.visit("/");
    cy.shown("start-workout").should("be.visible").and("be.enabled").click();
    cy.location("pathname").should("eq", "/workout");
    cy.shown("workout-name").should("have.text", "Workout");

    // Add two exercises through the search.
    addExerciseBySearch("bench press", BENCH, CURL);
    cy.shown("exercise-row-1").should("contain.text", "Barbell bench press");
    addExerciseBySearch("curl", CURL, BENCH);
    cy.shown("exercise-row-2").should("contain.text", "Barbell curl");

    // Open the bench press and log sets.
    cy.shown("exercise-row-1").click();
    cy.location("pathname").should("eq", "/workout/1");

    // Set 1: starts at an empty bar and the top of the rep range; marked as a warm-up, then ticked.
    cy.shown("add-set").should("be.visible").and("be.enabled").click();
    cy.shown("set-weight-1").should("have.text", "20");
    cy.shown("set-reps-1").should("have.text", "10");
    cy.shown("set-warmup-1").should("have.text", "1").click();
    cy.shown("set-warmup-1").should("have.attr", "aria-pressed", "true").and("have.text", "W");
    cy.shown("set-done-1").click();
    cy.shown("set-done-1").should("have.attr", "aria-pressed", "true");

    // Set 2: copies set 1's values but not the warm-up flag; step the weight up and the reps down.
    cy.shown("add-set").click();
    cy.shown("set-warmup-2").should("have.attr", "aria-pressed", "false").and("have.text", "1");
    cy.shown("set-weight-2").should("have.text", "20");
    cy.shown("set-weight-inc-2").click();
    cy.shown("set-weight-inc-2").click();
    cy.shown("set-weight-2").should("have.text", "25");
    cy.shown("set-reps-dec-2").click();
    cy.shown("set-reps-2").should("have.text", "9");
    cy.shown("set-done-2").should("have.attr", "aria-pressed", "false").click();
    cy.shown("set-done-2").should("have.attr", "aria-pressed", "true");

    // Set 3: a copy of set 2, ticked as is.
    cy.shown("add-set").click();
    cy.shown("set-weight-3").should("have.text", "25");
    cy.shown("set-reps-3").should("have.text", "9");
    cy.shown("set-done-3").click();
    cy.shown("set-done-3").should("have.attr", "aria-pressed", "true");

    // Back on the workout: 2 working sets done (the warm-up doesn't count) of 3.
    cy.shown("log-done").click();
    cy.location("pathname").should("eq", "/workout");
    cy.shown("exercise-row-1").should("contain.text", "2/3");
    cy.shown("exercise-row-2").should("contain.text", "0/3");

    // Finish: sets were done, so there is no question, and the summary opens.
    cy.request<WorkoutSession>("/api/sessions/active")
      .its("body.id")
      .then((sessionId: string) => {
        cy.shown("finish-workout").click();
        cy.location("pathname").should("eq", `/history/${sessionId}`);
      });
    cy.shown("session-detail-exercise-1").should("contain.text", "Barbell bench press");
    cy.shown("session-detail-set-1-1").should("have.text", "W 20 × 10");
    cy.shown("session-detail-set-1-2").should("have.text", "25 × 9");
    cy.shown("session-detail-set-1-3").should("have.text", "25 × 9");
    cy.shown("session-detail-exercise-2").should("contain.text", "Barbell curl");
    cy.shown("session-detail-set-2-1").should("not.exist");

    // Nothing is in progress any more.
    cy.request("/api/sessions/active").its("status").should("eq", 204);
  });

  it("a workout in progress can be resumed from home", () => {
    newUser("KG");
    cy.apiStartWorkout().then((session) => cy.apiAddExercise(session.id, CURL));

    cy.visit("/");
    cy.shown("resume-workout").should("be.visible").and("be.enabled");
    cy.shown("start-workout").should("not.exist");
    cy.shown("resume-workout").click();
    cy.location("pathname").should("eq", "/workout");
    cy.shown("workout-name").should("have.text", "Workout");
    cy.shown("exercise-row-1").should("contain.text", "Barbell curl");
    cy.shown("exercise-row-2").should("not.exist");
  });

  it("removing the last set asks first, then removes it", () => {
    newUser("KG");
    cy.apiStartWorkout().then((session) => {
      cy.wrap(session.id).as("sessionId");
      cy.apiAddExercise(session.id, BENCH);
      cy.apiAddSet(session.id, 1, { weightKg: 60, reps: 8, isWarmup: false, completed: true });
      cy.apiAddSet(session.id, 1, { weightKg: 60, reps: 8, isWarmup: false, completed: true });
      // The last set is a slip, never ticked off.
      cy.apiAddSet(session.id, 1, { weightKg: 62.5, reps: 6, isWarmup: false });
    });

    cy.visit("/workout/1");
    cy.shown("set-row-3").should("be.visible");
    // "No" keeps it.
    cy.shown("remove-last-set").should("be.visible").and("be.enabled").click();
    cy.shown("confirm-no").click();
    cy.shown("remove-last-set").should("be.visible");
    cy.shown("set-row-3").should("be.visible");

    // "Yes" removes only the last set; the ticked ones stay.
    cy.shown("remove-last-set").click();
    cy.shown("confirm-yes").click();
    cy.shown("set-row-3").should("not.exist");
    cy.shown("set-done-1").should("have.attr", "aria-pressed", "true");
    cy.shown("set-done-2").should("have.attr", "aria-pressed", "true");

    cy.get<string>("@sessionId").then((sessionId) => {
      cy.request<WorkoutSession>(`/api/sessions/${sessionId}`).its("body.exercises.0.sets").should("have.length", 2);
    });
  });

  it("discarding a workout asks first, then deletes it and goes home", () => {
    newUser("KG");
    cy.apiStartWorkout().then((session) => {
      cy.wrap(session.id).as("sessionId");
      cy.apiAddExercise(session.id, BENCH);
      cy.apiAddSet(session.id, 1, { weightKg: 60, reps: 8, isWarmup: false, completed: true });
    });

    cy.visit("/workout");
    // "No" keeps the workout.
    cy.shown("discard-workout").should("be.visible").and("be.enabled").click();
    cy.shown("confirm-no").click();
    cy.shown("discard-workout").should("be.visible");
    cy.location("pathname").should("eq", "/workout");

    // "Yes" deletes it.
    cy.shown("discard-workout").click();
    cy.shown("confirm-yes").click();
    cy.location("pathname").should("eq", "/");
    cy.shown("start-workout").should("be.visible");
    cy.shown("resume-workout").should("not.exist");

    cy.request("/api/sessions/active").its("status").should("eq", 204);
    cy.get<string>("@sessionId").then((sessionId) => {
      cy.request({ url: `/api/sessions/${sessionId}`, failOnStatusCode: false }).its("status").should("eq", 404);
    });
  });

  it("finishing with no sets done asks first: No stays, Yes finishes", () => {
    newUser("KG");
    cy.apiStartWorkout().then((session) => {
      cy.wrap(session.id).as("sessionId");
      cy.apiAddExercise(session.id, BENCH);
      // Logged but never ticked off.
      cy.apiAddSet(session.id, 1, { weightKg: 60, reps: 8, isWarmup: false });
    });

    cy.visit("/workout");
    cy.shown("finish-workout").should("be.visible").and("be.enabled").click();
    cy.shown("confirm-no").click();
    cy.shown("finish-workout").should("be.visible");
    cy.location("pathname").should("eq", "/workout");
    cy.request("/api/sessions/active").its("status").should("eq", 200);

    cy.shown("finish-workout").click();
    cy.shown("confirm-yes").click();
    cy.get<string>("@sessionId").then((sessionId) => {
      cy.location("pathname").should("eq", `/history/${sessionId}`);
    });
    // The un-ticked set is still listed in the summary.
    cy.shown("session-detail-set-1-1").should("have.text", "60 × 8");
    cy.request("/api/sessions/active").its("status").should("eq", 204);
  });

  it("removing an exercise asks first, then takes it off the workout", () => {
    newUser("KG");
    cy.apiStartWorkout().then((session) => {
      cy.apiAddExercise(session.id, BENCH);
      cy.apiAddExercise(session.id, CURL);
      cy.apiAddSet(session.id, 1, { weightKg: 60, reps: 8, isWarmup: false, completed: true });
    });

    cy.visit("/workout/1");
    cy.shown("set-row-1").should("be.visible");
    // "No" keeps it.
    cy.shown("remove-exercise").should("be.visible").and("be.enabled").click();
    cy.shown("confirm-no").click();
    cy.shown("remove-exercise").should("be.visible");
    cy.location("pathname").should("eq", "/workout/1");

    // "Yes" removes it; the curl moves up to number 1.
    cy.shown("remove-exercise").click();
    cy.shown("confirm-yes").click();
    cy.location("pathname").should("eq", "/workout");
    cy.shown("exercise-row-1").should("contain.text", "Barbell curl");
    cy.shown("exercise-row-2").should("not.exist");
  });

  it("a pound user sees and steps weights in pounds, and kg are saved", () => {
    newUser("LB");
    cy.apiStartWorkout().then((session) => {
      cy.wrap(session.id).as("sessionId");
      cy.apiAddExercise(session.id, BENCH);
    });

    cy.visit("/workout/1");
    // The empty bar is 45 lb (20 kg); one tap adds 5 lb.
    cy.shown("add-set").should("be.visible").and("be.enabled").click();
    cy.shown("set-weight-1").should("have.text", "45");
    cy.shown("set-weight-inc-1").click();
    cy.shown("set-weight-1").should("have.text", "50");
    cy.shown("set-done-1").click();
    cy.shown("set-done-1").should("have.attr", "aria-pressed", "true");
    cy.shown("set-weight-1").should("have.text", "50");

    // The API keeps kg: 50 lb ÷ 2.20462 = 22.68 kg.
    cy.get<string>("@sessionId").then((sessionId) => {
      cy.request<WorkoutSession>(`/api/sessions/${sessionId}`)
        .its("body.exercises.0.sets.0.weightKg")
        .then(Number)
        .should("be.closeTo", 22.68, 0.001);

      // The summary shows pounds too.
      cy.apiFinish(sessionId);
      cy.visit(`/history/${sessionId}`);
    });
    cy.shown("session-detail-set-1-1").should("have.text", "50 × 10");
  });
});
