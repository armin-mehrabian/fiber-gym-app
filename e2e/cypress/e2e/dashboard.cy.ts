// The weekly dashboard (Home): week %, day strip, body figure with region info, gaps and this week's workouts.

import { PASSWORD, TIMEZONE } from "../support/testUser";

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

  it("a new user sees an empty week with today marked", () => {
    cy.visit("/");
    cy.shown("week-percent").should("have.text", "0");

    cy.get('[data-testid^="day-"]:visible').should("have.length", 7);
    cy.get('[data-testid^="day-"]:visible').each((day) => {
      cy.wrap(day).should("have.attr", "data-trained", "false");
    });
    cy.shown(`day-${todayInZone()}`).should("have.attr", "aria-current", "date");
    cy.get('[aria-current="date"]:visible').should("have.length", 1);

    cy.shown("week-empty").should("be.visible");
  });

  it("a finished workout fills the week and tapping its region shows the sets behind it", () => {
    // Only the 3 ticked working sets count: the warm-up and the un-ticked set don't.
    apiFinishedWorkout("incline_barbell_press", [
      { weightKg: 20, reps: 10, isWarmup: true, completed: true },
      ...THREE_WORKING_SETS,
      { weightKg: 60, reps: 6, isWarmup: false },
    ]).as("workout");

    cy.visit("/");
    cy.shown("week-percent")
      .invoke("text")
      .then((text) => {
        expect(Number(text)).to.be.greaterThan(0);
      });
    cy.shown(`day-${todayInZone()}`).should("have.attr", "data-trained", "true");

    // Tapping the upper chest opens its info.
    tapRegion("chest_upper");
    cy.shown("region-info").should("be.visible");
    cy.shown("region-info-name").should("have.text", "Upper chest");
    cy.shown("region-info-sets").should("have.text", "3 of 6");
    cy.shown("region-info-contribution-0").should("have.text", `Incline barbell press · ${weekdayInZone()} · 3`);
    cy.shown("region-info-contribution-1").should("not.exist");

    // Tapping it again closes the info.
    tapRegion("chest_upper");
    cy.shown("region-info").should("not.exist");

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
    apiFinishedWorkout("incline_barbell_press", THREE_WORKING_SETS);

    cy.visit("/");
    tapRegion("chest_mid");
    cy.shown("region-info-name").should("have.text", "Mid chest");
    cy.shown("region-info-sets").should("have.text", "1.5 of 6");
    cy.shown("region-info-contribution-0").should("have.text", `Incline barbell press · ${weekdayInZone()} · 1.5`);
  });

  it("switching to the back view shows the back regions in the gaps list", () => {
    cy.visit("/");
    cy.shown("view-front").should("have.attr", "aria-pressed", "true");
    cy.shown("view-back").should("have.attr", "aria-pressed", "false");
    // Front view: front-only regions like the upper chest are listed, back-only ones are not.
    cy.shown("gap-chest_upper").should("be.visible");
    cy.shown("gap-delt_rear").should("not.exist");

    cy.shown("view-back").click();
    cy.shown("view-back").should("have.attr", "aria-pressed", "true");
    cy.shown("view-front").should("have.attr", "aria-pressed", "false");
    cy.shown("gap-delt_rear").should("be.visible");
    cy.shown("gap-chest_upper").should("not.exist");
    // Side delts are drawn on both sides, so they stay in the list.
    cy.shown("gap-delt_side").should("be.visible");
  });

  it("a region trained hard in the last 48 hours shows as recovering", () => {
    // Barbell row: lower lats get 3 sets (recovering), the lower back only helps (1.5 sets).
    apiFinishedWorkout("barbell_row", THREE_WORKING_SETS);

    cy.visit("/");
    cy.shown("view-back").click();
    cy.shown("view-back").should("have.attr", "aria-pressed", "true");

    tapRegion("lats_lower");
    cy.shown("region-info-name").should("have.text", "Lower lats");
    cy.shown("region-info-sets").should("have.text", "3 of 6");
    cy.shown("region-info-recovering").should("be.visible");

    tapRegion("lower_back");
    cy.shown("region-info-name").should("have.text", "Lower back (erectors)");
    cy.shown("region-info-sets").should("have.text", "1.5 of 4");
    cy.shown("region-info-recovering").should("not.exist");
  });
});
