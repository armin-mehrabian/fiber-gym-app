// The weekly dashboard (Home): day strip, body figure with region info in words, group badges,
// the "still dark" list and this week's workouts. Home shows words, not numbers.

import { PASSWORD, TIMEZONE } from "../support/testUser";

type Region = { id: string };

// The six muscle groups, in the order the badges are shown.
const GROUPS = ["chest", "shoulders", "back", "arms", "core", "legs"];

// Today's date (YYYY-MM-DD) on the user's own calendar. "en-CA" writes dates as YYYY-MM-DD.
function todayInZone(): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: TIMEZONE }).format(new Date());
}

// Today's short weekday name ("Mon"…"Sun") on the user's calendar, as used in contribution lines.
function weekdayInZone(): string {
  return new Intl.DateTimeFormat("en-GB", { weekday: "short", timeZone: TIMEZONE }).format(new Date());
}

// Taps a region on the big body figure. Each region is drawn twice (left and mirrored right half),
// and small read-only figures on Home draw it too; only the left half of the big figure is a button.
function tapRegion(regionId: string) {
  cy.get(`[data-region="${regionId}"][role="button"]:visible`).click();
}

// Finishes a workout through the API with one exercise and the given sets; yields the workout.
function apiFinishedWorkout(exerciseId: string, sets: SetSetup[]): Cypress.Chainable<WorkoutSession> {
  return cy.apiStartWorkout().then((session) => {
    cy.apiAddExercise(session.id, exerciseId);
    sets.forEach((set) => cy.apiAddSet(session.id, 1, set));
    return cy.apiFinish(session.id);
  });
}

// Finishes a workout through the API where each exercise gets `sets` finished working sets.
function apiFinishedCompleted(exerciseIds: string[], sets: number): Cypress.Chainable<WorkoutSession> {
  return cy.apiStartWorkout({ exercises: exerciseIds.map((exerciseId) => ({ exerciseId })) }).then((session) => {
    exerciseIds.forEach((_, index) => cy.apiCompleteExercise(session.id, index + 1, { weightKg: 60, reps: 8, sets }));
    return cy.apiFinish(session.id);
  });
}

// Three ticked working sets.
const THREE_WORKING_SETS: SetSetup[] = [
  { weightKg: 60, reps: 8, isWarmup: false, completed: true },
  { weightKg: 60, reps: 8, isWarmup: false, completed: true },
  { weightKg: 60, reps: 7, isWarmup: false, completed: true },
];

describe("Dashboard", () => {
  beforeEach(() => {
    cy.uniqueEmail().then((email) => {
      cy.apiSignup(email, PASSWORD);
      cy.apiOnboard({ splitType: "PPL", units: "KG", equipment: ["barbell", "bench"] });
    });
  });

  it("a new user sees an empty, dark week in words, without percentages or set counts", () => {
    cy.visit("/");
    cy.shown("wordmark").should("be.visible");

    cy.get('[data-testid^="day-"]:visible').should("have.length", 7);
    cy.get('[data-testid^="day-"]:visible').each((day) => {
      cy.wrap(day).should("have.attr", "data-trained", "false");
    });
    cy.shown(`day-${todayInZone()}`).should("have.attr", "aria-current", "date");
    cy.get('[aria-current="date"]:visible').should("have.length", 1);
    cy.shown("week-empty").should("be.visible");

    // Every badge is still to earn.
    GROUPS.forEach((group) => cy.shown(`badge-${group}`).should("have.attr", "data-done", "false"));
    cy.get('[data-testid^="badge-"]:visible').should("have.length", GROUPS.length);

    // The still-dark list names untouched regions of the front.
    cy.shown("dark-chest_upper").should("be.visible").and("have.text", "Upper chest");
    cy.shown("dark-none").should("not.exist");

    // The old number displays are gone, and no percentage or "x of y" is written anywhere.
    cy.shown("week-percent").should("not.exist");
    cy.get('[data-testid^="gap-"]').should("not.exist");
    cy.shown("next-up").invoke("text").should("not.match", /\d/);
    cy.document().its("body.innerText").should("not.contain", "%").and("not.match", /\d\s*of\s*\d/);

    // An untouched region reads "Not yet", with no number.
    tapRegion("chest_upper");
    cy.shown("region-info-state").should("have.text", "Not yet");
    cy.shown("region-info-sets").should("not.exist");
    cy.shown("region-info-contribution-0").should("not.exist");
    cy.shown("region-info").invoke("text").should("not.match", /\d/);
  });

  it("a finished workout lights its regions, and tapping one tells in words what trained it", () => {
    // Only the 3 ticked working sets count: the warm-up and the un-ticked set don't. With the upper
    // chest's target at 4, those 3 sets read "Started"; counting either extra set would make it "Done ✓".
    cy.apiSetTargets([{ regionId: "chest_upper", weeklySets: 4 }]);
    apiFinishedWorkout("incline_barbell_press", [
      { weightKg: 20, reps: 10, isWarmup: true, completed: true },
      ...THREE_WORKING_SETS,
      { weightKg: 60, reps: 6, isWarmup: false },
    ]).as("workout");

    cy.visit("/");
    cy.shown(`day-${todayInZone()}`).should("have.attr", "data-trained", "true");

    // Tapping the upper chest opens its info: started (3 of its 4 sets), with the exercise and the day.
    tapRegion("chest_upper");
    cy.shown("region-info").should("be.visible");
    cy.shown("region-info-name").should("have.text", "Upper chest");
    cy.shown("region-info-state").should("have.text", "Started");
    cy.shown("region-info-contribution-0").should("have.text", `Incline barbell press · ${weekdayInZone()}`);
    cy.shown("region-info-contribution-1").should("not.exist");
    cy.shown("region-info-sets").should("not.exist");

    // Tapping it again closes the info.
    tapRegion("chest_upper");
    cy.shown("region-info").should("not.exist");

    // Trained regions leave the still-dark list; the untouched lower chest stays on it.
    cy.shown("dark-chest_upper").should("not.exist");
    cy.shown("dark-chest_mid").should("not.exist");
    cy.shown("dark-chest_lower").should("be.visible");

    // The workout is listed under "This week" and opens its detail page.
    cy.shown("week-empty").should("not.exist");
    cy.get<WorkoutSession>("@workout").then((workout) => {
      cy.shown(`week-session-${workout.id}`)
        .should("be.visible")
        .and("have.attr", "href", `/history/${workout.id}`)
        .and("contain.text", "3 sets");
      cy.shown(`week-session-${workout.id}`).click();
      cy.location("pathname").should("eq", `/history/${workout.id}`);
    });
  });

  it("a helping region counts each set as half", () => {
    // Incline barbell press: upper chest is the main region, mid chest only helps.
    // With both targets at 4, four sets finish the upper chest but only half of the mid chest.
    cy.apiSetTargets([
      { regionId: "chest_upper", weeklySets: 4 },
      { regionId: "chest_mid", weeklySets: 4 },
    ]);
    apiFinishedCompleted(["incline_barbell_press"], 4);

    cy.visit("/");
    tapRegion("chest_upper");
    cy.shown("region-info-name").should("have.text", "Upper chest");
    cy.shown("region-info-state").should("have.text", "Done ✓");

    tapRegion("chest_mid");
    cy.shown("region-info-name").should("have.text", "Mid chest");
    cy.shown("region-info-state").should("have.text", "Started");
    cy.shown("region-info-contribution-0").should("have.text", `Incline barbell press · ${weekdayInZone()}`);
  });

  it("finishing every part of a group earns its badge, and its regions read done", () => {
    // Six sets each of incline, flat and decline press reach the target (6) of all three chest regions.
    apiFinishedCompleted(["incline_barbell_press", "barbell_bench_press", "decline_barbell_press"], 6);

    cy.visit("/");
    cy.shown("badge-chest").should("have.attr", "data-done", "true");
    // The other groups are only partly trained (or not at all).
    GROUPS.filter((group) => group !== "chest").forEach((group) =>
      cy.shown(`badge-${group}`).should("have.attr", "data-done", "false"),
    );

    tapRegion("chest_lower");
    cy.shown("region-info-name").should("have.text", "Lower chest");
    cy.shown("region-info-state").should("have.text", "Done ✓");
    cy.shown("region-info-contribution-0").should("have.text", `Decline barbell press · ${weekdayInZone()}`);

    cy.get('[data-testid^="dark-chest_"]').should("not.exist");
  });

  it("switching to the back view shows the back regions in the still-dark list", () => {
    cy.visit("/");
    cy.shown("view-front").should("have.attr", "aria-pressed", "true");
    cy.shown("view-back").should("have.attr", "aria-pressed", "false");
    // Front view: front-only regions like the upper chest are listed, back-only ones are not.
    cy.shown("dark-chest_upper").should("be.visible");
    cy.shown("dark-delt_rear").should("not.exist");

    cy.shown("view-back").click();
    cy.shown("view-back").should("have.attr", "aria-pressed", "true");
    cy.shown("view-front").should("have.attr", "aria-pressed", "false");
    cy.shown("dark-delt_rear").should("be.visible");
    cy.shown("dark-chest_upper").should("not.exist");
    // Side delts are drawn on both sides, so they stay in the list.
    cy.shown("dark-delt_side").should("be.visible");
  });

  it("with nothing left to train, nothing is dark and every badge is done", () => {
    // A weekly target of 0 means the user doesn't train that region, so it counts as done.
    cy.request<Region[]>("/api/regions")
      .its("body")
      .then((regions) => {
        cy.apiSetTargets(regions.map((region) => ({ regionId: region.id, weeklySets: 0 })));
      });

    cy.visit("/");
    cy.shown("dark-none").should("be.visible");
    cy.get('[data-testid^="dark-"]:visible').should("have.length", 1);
    cy.shown("view-back").click();
    cy.shown("view-back").should("have.attr", "aria-pressed", "true");
    cy.shown("dark-none").should("be.visible");
    GROUPS.forEach((group) => cy.shown(`badge-${group}`).should("have.attr", "data-done", "true"));
  });

  it("a region trained hard in the last 48 hours shows as recovering", () => {
    // Barbell row: lower lats get 3 sets (recovering), the lower back only helps (1.5 sets).
    apiFinishedWorkout("barbell_row", THREE_WORKING_SETS);

    cy.visit("/");
    cy.shown("view-back").click();
    cy.shown("view-back").should("have.attr", "aria-pressed", "true");

    tapRegion("lats_lower");
    cy.shown("region-info-name").should("have.text", "Lower lats");
    cy.shown("region-info-state").should("have.text", "Started");
    cy.shown("region-info-recovering").should("be.visible");

    tapRegion("lower_back");
    cy.shown("region-info-name").should("have.text", "Lower back (erectors)");
    cy.shown("region-info-state").should("have.text", "Started");
    cy.shown("region-info-recovering").should("not.exist");
  });
});
